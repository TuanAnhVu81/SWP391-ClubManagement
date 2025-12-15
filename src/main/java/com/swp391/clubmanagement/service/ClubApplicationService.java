package com.swp391.clubmanagement.service;

import com.swp391.clubmanagement.dto.request.ClubApplicationRequest;
import com.swp391.clubmanagement.dto.request.ReviewApplicationRequest;
import com.swp391.clubmanagement.dto.response.ClubApplicationResponse;
import com.swp391.clubmanagement.entity.ClubApplications;
import com.swp391.clubmanagement.entity.Clubs;
import com.swp391.clubmanagement.entity.Memberships;
import com.swp391.clubmanagement.entity.Registers;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.enums.ClubRoleType;
import com.swp391.clubmanagement.enums.JoinStatus;
import com.swp391.clubmanagement.enums.RequestStatus;
import com.swp391.clubmanagement.exception.AppException;
import com.swp391.clubmanagement.exception.ErrorCode;
import com.swp391.clubmanagement.mapper.ClubApplicationMapper;
import com.swp391.clubmanagement.enums.RoleType;
import com.swp391.clubmanagement.repository.ClubApplicationRepository;
import com.swp391.clubmanagement.repository.ClubRepository;
import com.swp391.clubmanagement.repository.MembershipRepository;
import com.swp391.clubmanagement.repository.RegisterRepository;
import com.swp391.clubmanagement.repository.RoleRepository;
import com.swp391.clubmanagement.repository.UserRepository;
import com.swp391.clubmanagement.utils.DateTimeUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ClubApplicationService {
    
    ClubApplicationRepository clubApplicationRepository;
    ClubRepository clubRepository;
    MembershipRepository membershipRepository;
    RegisterRepository registerRepository;
    UserRepository userRepository;
    RoleRepository roleRepository;
    ClubApplicationMapper clubApplicationMapper;
    
    /**
     * Tạo đơn yêu cầu thành lập CLB mới (Student)
     */
    @Transactional
    public ClubApplicationResponse createClubApplication(ClubApplicationRequest request) {
        // Lấy thông tin user hiện tại từ Security Context
        // authentication.getName() trả về subject của JWT, tức là email
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Users creator = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        // Tạo đơn mới
        ClubApplications application = ClubApplications.builder()
                .creator(creator)
                .proposedName(request.getProposedName())
                .category(request.getCategory())
                .purpose(request.getPurpose())
                .description(request.getDescription())
                .location(request.getLocation())
                .email(request.getEmail())
                .defaultMembershipFee(request.getDefaultMembershipFee())
                .status(RequestStatus.DangCho)
                .createdAt(DateTimeUtils.nowVietnam())
                .build();
        
        application = clubApplicationRepository.save(application);
        log.info("Created club application: {} by user: {}", application.getRequestId(), creator.getEmail());
        
        return clubApplicationMapper.toResponse(application);
    }
    
    /**
     * Xem danh sách các đơn yêu cầu mở CLB (Admin) - Có thể filter theo status
     */
    public List<ClubApplicationResponse> getAllApplications(RequestStatus status) {
        List<ClubApplications> applications;
        
        if (status != null) {
            applications = clubApplicationRepository.findByStatus(status);
        } else {
            applications = clubApplicationRepository.findAllByOrderByCreatedAtDesc();
        }
        
        return applications.stream()
                .map(clubApplicationMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Xem lịch sử các đơn mình đã gửi (Student)
     */
    public List<ClubApplicationResponse> getMyApplications() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Users creator = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        List<ClubApplications> applications = clubApplicationRepository.findByCreatorOrderByCreatedAtDesc(creator);
        
        return applications.stream()
                .map(clubApplicationMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Duyệt hoặc từ chối đơn (Admin)
     * Khi duyệt (ChapThuan), hệ thống tự động tạo CLB mới
     */
    @Transactional
    public ClubApplicationResponse reviewApplication(Integer requestId, ReviewApplicationRequest request) {
        // Lấy thông tin admin reviewer
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Users reviewer = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        // Tìm đơn
        ClubApplications application = clubApplicationRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.APPLICATION_NOT_FOUND));
        
        // Kiểm tra đơn đã được review chưa
        if (application.getStatus() != RequestStatus.DangCho) {
            throw new AppException(ErrorCode.APPLICATION_ALREADY_REVIEWED);
        }
        
        // Validate status (chỉ cho phép ChapThuan hoặc TuChoi)
        if (request.getStatus() != RequestStatus.ChapThuan && request.getStatus() != RequestStatus.TuChoi) {
            throw new AppException(ErrorCode.INVALID_APPLICATION_STATUS);
        }
        
        // Cập nhật thông tin review
        application.setStatus(request.getStatus());
        application.setAdminNote(request.getAdminNote());
        application.setReviewer(reviewer);
        application.setUpdatedAt(DateTimeUtils.nowVietnam());
        
        // Nếu duyệt, tự động tạo CLB mới và gói membership mặc định
        if (request.getStatus() == RequestStatus.ChapThuan) {
            // 1. Tạo CLB mới
            Clubs newClub = Clubs.builder()
                    .clubName(application.getProposedName())
                    .category(application.getCategory())
                    .description(application.getDescription())
                    .location(application.getLocation())
                    .email(application.getEmail())
                    .founder(application.getCreator())
                    .isActive(true)
                    .build();
            
            newClub = clubRepository.save(newClub);
            application.setClub(newClub);
            
            log.info("Club created: {} from application: {}", newClub.getClubId(), requestId);
            
            // 2. Tự động tạo gói membership mặc định
            Memberships defaultPackage = Memberships.builder()
                    .club(newClub)
                    .packageName("Thành Viên Cơ Bản")
                    .term("1 tháng")
                    .price(application.getDefaultMembershipFee())
                    .description("Gói thành viên mặc định được tạo tự động khi CLB được phê duyệt")
                    .isActive(true)
                    .build();
            
            defaultPackage = membershipRepository.save(defaultPackage);
            log.info("Default membership package created for club: {} with price: {}", 
                    newClub.getClubId(), application.getDefaultMembershipFee());
            
            // 3. Tự động thêm founder vào CLB với role ChuTich
            LocalDateTime now = DateTimeUtils.nowVietnam();
            Registers founderRegistration = Registers.builder()
                    .user(application.getCreator())
                    .membershipPackage(defaultPackage)
                    .status(JoinStatus.DaDuyet)
                    .clubRole(ClubRoleType.ChuTich) // Founder tự động là Chủ tịch
                    .approver(reviewer) // Admin duyệt đơn cũng duyệt luôn founder
                    .isPaid(true) // Miễn phí cho founder
                    .paymentMethod("MIỄN PHÍ - NGƯỜI SÁNG LẬP")
                    .paymentDate(now)
                    .startDate(now)
                    .endDate(now.plusYears(10)) // Founder có membership vĩnh viễn (10 năm)
                    .joinDate(now)
                    .createdAt(now)
                    .build();
            
            // Đảm bảo clubRole được set đúng trước khi save
            founderRegistration.setClubRole(ClubRoleType.ChuTich);
            founderRegistration = registerRepository.save(founderRegistration);
            
            // Verify sau khi save
            if (founderRegistration.getClubRole() != ClubRoleType.ChuTich) {
                log.error("ERROR: Founder clubRole was not saved correctly! Expected: ChuTich, Actual: {}", 
                        founderRegistration.getClubRole());
                // Set lại và save lại
                founderRegistration.setClubRole(ClubRoleType.ChuTich);
                founderRegistration = registerRepository.save(founderRegistration);
            }
            
            log.info("Founder {} automatically added as ChuTich of club {} with subscriptionId: {} and clubRole: {}", 
                    application.getCreator().getEmail(), newClub.getClubId(), 
                    founderRegistration.getSubscriptionId(), founderRegistration.getClubRole());
            
            // 4. Cập nhật Role của founder thành ChuTich trong bảng Users
            // Lấy lại user từ database để đảm bảo có entity mới nhất
            Users founder = userRepository.findById(application.getCreator().getUserId())
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            
            var chuTichRole = roleRepository.findByRoleName(RoleType.ChuTich)
                    .orElseThrow(() -> {
                        log.error("ChuTich role not found in database. Please check ApplicationInitConfig.");
                        return new RuntimeException("ChuTich role not found in database");
                    });
            
            founder.setRole(chuTichRole);
            founder = userRepository.save(founder);
            userRepository.flush(); // Đảm bảo dữ liệu được flush vào database ngay lập tức
            log.info("Founder {} role updated to ChuTich. Role ID: {}, Role Name: {}", 
                    founder.getEmail(), founder.getRole().getRoleId(), founder.getRole().getRoleName());
        }
        
        application = clubApplicationRepository.save(application);
        log.info("Application {} reviewed by {}: {}", requestId, reviewer.getEmail(), request.getStatus());
        
        return clubApplicationMapper.toResponse(application);
    }
}
