// Package định nghĩa service layer - xử lý business logic cho quản lý đơn yêu cầu thành lập CLB
package com.swp391.clubmanagement.service;

// ========== DTO (Data Transfer Object) ==========
import com.swp391.clubmanagement.dto.request.ClubApplicationRequest; // Request tạo đơn từ Student
import com.swp391.clubmanagement.dto.request.ReviewApplicationRequest; // Request duyệt/từ chối từ Admin
import com.swp391.clubmanagement.dto.response.ClubApplicationResponse; // Response trả về thông tin đơn

// ========== Entity (Database Entities) ==========
import com.swp391.clubmanagement.entity.ClubApplications; // Bảng đơn yêu cầu thành lập CLB
import com.swp391.clubmanagement.entity.Clubs; // Bảng CLB
import com.swp391.clubmanagement.entity.Memberships; // Bảng gói membership
import com.swp391.clubmanagement.entity.Registers; // Bảng đăng ký tham gia CLB
import com.swp391.clubmanagement.entity.Users; // Bảng người dùng

// ========== Enum (Enumeration Types) ==========
import com.swp391.clubmanagement.enums.ClubRoleType; // Vai trò trong CLB: ChuTich, PhoChuTich, etc.
import com.swp391.clubmanagement.enums.JoinStatus; // Trạng thái tham gia: DangCho, DaDuyet, TuChoi
import com.swp391.clubmanagement.enums.RequestStatus; // Trạng thái đơn: DangCho, ChapThuan, TuChoi
import com.swp391.clubmanagement.enums.RoleType; // Vai trò hệ thống: Student, Admin, ChuTich

// ========== Exception Handling ==========
import com.swp391.clubmanagement.exception.AppException; // Custom exception
import com.swp391.clubmanagement.exception.ErrorCode; // Mã lỗi hệ thống

// ========== Mapper ==========
import com.swp391.clubmanagement.mapper.ClubApplicationMapper; // Chuyển đổi Entity <-> DTO

// ========== Repository (Data Access Layer) ==========
import com.swp391.clubmanagement.repository.ClubApplicationRepository; // Repository cho bảng ClubApplications
import com.swp391.clubmanagement.repository.ClubRepository; // Repository cho bảng Clubs
import com.swp391.clubmanagement.repository.MembershipRepository; // Repository cho bảng Memberships
import com.swp391.clubmanagement.repository.RegisterRepository; // Repository cho bảng Registers
import com.swp391.clubmanagement.repository.RoleRepository; // Repository cho bảng Roles
import com.swp391.clubmanagement.repository.UserRepository; // Repository cho bảng Users

// ========== Utilities ==========
import com.swp391.clubmanagement.utils.DateTimeUtils; // Xử lý thời gian theo múi giờ Việt Nam

// ========== Lombok (Code Generation) ==========
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor; // Tự động tạo constructor với các field final
import lombok.experimental.FieldDefaults; // Tự động thêm private final cho fields
import lombok.extern.slf4j.Slf4j; // Tự động tạo logger

// ========== Spring Framework ==========
import org.springframework.security.core.context.SecurityContextHolder; // Lấy thông tin user hiện tại từ JWT
import org.springframework.stereotype.Service; // Đánh dấu class là Spring Service Bean
import org.springframework.transaction.annotation.Transactional; // Quản lý transaction (all-or-nothing)

// ========== Java Standard Library ==========
import java.time.LocalDateTime; // Đại diện ngày giờ
import java.util.List; // Danh sách
import java.util.stream.Collectors; // Collect stream thành collection

/**
 * Service class quản lý các đơn yêu cầu thành lập CLB
 * 
 * Chức năng chính:
 * - Student: Tạo đơn, xem lịch sử đơn của mình
 * - Admin: Xem tất cả đơn, duyệt/từ chối đơn (khi duyệt tự động tạo CLB mới)
 * 
 * @Service: Spring Service Bean, được quản lý bởi IoC Container
 * @RequiredArgsConstructor: Lombok tự động tạo constructor inject dependencies
 * @FieldDefaults: Tự động thêm private final cho các field
 * @Slf4j: Tự động tạo logger với tên "log"
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ClubApplicationService {
    
    /** Repository thao tác với bảng club_applications - lưu trữ các đơn yêu cầu thành lập CLB */
    ClubApplicationRepository clubApplicationRepository;
    
    /** Repository thao tác với bảng clubs - lưu trữ thông tin các CLB đã được phê duyệt */
    ClubRepository clubRepository;
    
    /** Repository thao tác với bảng memberships - lưu trữ các gói membership của CLB */
    MembershipRepository membershipRepository;
    
    /** Repository thao tác với bảng registers - lưu trữ thông tin đăng ký tham gia CLB */
    RegisterRepository registerRepository;
    
    /** Repository thao tác với bảng users - lưu trữ thông tin người dùng */
    UserRepository userRepository;
    
    /** Repository thao tác với bảng roles - lưu trữ các vai trò trong hệ thống */
    RoleRepository roleRepository;
    
    /** Mapper chuyển đổi Entity (ClubApplications) <-> DTO (ClubApplicationResponse) */
    ClubApplicationMapper clubApplicationMapper;
    
    /**
     * Tạo đơn yêu cầu thành lập CLB mới
     * 
     * Business Rules:
     * - User không được là thành viên active của CLB nào khác (status=DaDuyet, isPaid=true)
     * - Đơn mới luôn có status = DangCho (chờ Admin duyệt)
     * 
     * @param request Thông tin đơn: tên CLB, category, purpose, description, location, email, phí membership
     * @return ClubApplicationResponse DTO chứa thông tin đơn đã tạo
     * @throws AppException USER_NOT_FOUND nếu không tìm thấy user
     * @throws AppException ALREADY_CLUB_MEMBER nếu user đã là thành viên active của CLB khác
     * 
     * @Transactional Đảm bảo toàn bộ operation chạy trong 1 transaction (rollback nếu lỗi)
     */
    @Transactional
    public ClubApplicationResponse createClubApplication(ClubApplicationRequest request) {
        // Lấy thông tin user hiện tại từ Security Context
        // authentication.getName() trả về subject của JWT, tức là email
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Users creator = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        // KIỂM TRA: User không được là thành viên active của CLB nào khác
        // Kiểm tra dựa trên bảng Registers (không dựa vào Role trong Users vì role có thể thay đổi)
        // Active member = status DaDuyet + isPaid = true
        List<Registers> activeRegistrations = registerRepository.findByUserAndStatusAndIsPaid(
                creator, JoinStatus.DaDuyet, true);
        
        if (!activeRegistrations.isEmpty()) {
            // User đang là thành viên active của ít nhất 1 CLB
            log.warn("User {} is already an active member of {} club(s). Cannot create new club application.", 
                    creator.getEmail(), activeRegistrations.size());
            throw new AppException(ErrorCode.ALREADY_CLUB_MEMBER);
        }
        
        // Tạo đơn mới với Builder Pattern
        ClubApplications application = ClubApplications.builder()
                .creator(creator) // User tạo đơn (sẽ là founder nếu được duyệt)
                .proposedName(request.getProposedName()) // Tên CLB đề xuất
                .category(request.getCategory()) // Danh mục CLB
                .purpose(request.getPurpose()) // Mục đích thành lập
                .description(request.getDescription()) // Mô tả chi tiết
                .location(request.getLocation()) // Địa điểm hoạt động
                .email(request.getEmail()) // Email liên hệ
                .defaultMembershipFee(request.getDefaultMembershipFee()) // Phí membership mặc định
                .status(RequestStatus.DangCho) // Trạng thái: Đang chờ duyệt
                .createdAt(DateTimeUtils.nowVietnam()) // Thời gian tạo (múi giờ VN)
                .build();
        
        // Lưu vào database
        application = clubApplicationRepository.save(application);
        log.info("Created club application: {} by user: {}", application.getRequestId(), creator.getEmail());
        
        // Chuyển đổi Entity sang DTO và trả về
        return clubApplicationMapper.toResponse(application);
    }
    
    /**
     * Xem danh sách tất cả các đơn yêu cầu thành lập CLB
     * 
     * Tính năng:
     * - Có thể filter theo status (DangCho, ChapThuan, TuChoi)
     * - Nếu không filter, trả về tất cả đơn sắp xếp theo thời gian tạo (mới nhất trước)
     * 
     * @param status Trạng thái để filter (null = lấy tất cả)
     * @return List<ClubApplicationResponse> Danh sách đơn đã chuyển đổi sang DTO
     */
    public List<ClubApplicationResponse> getAllApplications(RequestStatus status) {
        List<ClubApplications> applications;
        
        // Nếu có filter theo status -> lấy đơn có status tương ứng
        // Nếu không -> lấy tất cả đơn sắp xếp theo createdAt DESC (mới nhất trước)
        if (status != null) {
            applications = clubApplicationRepository.findByStatus(status);
        } else {
            applications = clubApplicationRepository.findAllByOrderByCreatedAtDesc();
        }
        
        // Chuyển đổi Entity sang DTO bằng Stream API
        return applications.stream()
                .map(clubApplicationMapper::toResponse) // Method reference: application -> mapper.toResponse(application)
                .collect(Collectors.toList()); // Thu thập kết quả thành List
    }
    
    /**
     * Xem lịch sử các đơn yêu cầu mà user hiện tại đã gửi
     * 
     * Tính năng:
     * - Chỉ hiển thị đơn của user hiện tại (tự động lấy từ JWT token)
     * - Sắp xếp theo thời gian tạo giảm dần (đơn mới nhất trước)
     * - Hiển thị tất cả đơn bất kể status
     * 
     * @return List<ClubApplicationResponse> Danh sách đơn của user hiện tại
     * @throws AppException USER_NOT_FOUND nếu không tìm thấy user
     */
    public List<ClubApplicationResponse> getMyApplications() {
        // Lấy email từ JWT token (đã được validate ở Security Filter)
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Users creator = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        // Tìm tất cả đơn có creator = user hiện tại, sắp xếp theo createdAt DESC
        List<ClubApplications> applications = clubApplicationRepository.findByCreatorOrderByCreatedAtDesc(creator);
        
        // Chuyển đổi Entity sang DTO
        return applications.stream()
                .map(clubApplicationMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Duyệt hoặc từ chối đơn yêu cầu thành lập CLB
     * 
     * Business Logic:
     * - Chỉ có thể duyệt/từ chối đơn đang ở trạng thái DangCho
     * - Khi duyệt (ChapThuan): Tự động tạo CLB mới, gói membership mặc định, thêm founder với role ChuTich
     * - Khi từ chối (TuChoi): Chỉ cập nhật status và admin note
     * 
     * @param requestId ID của đơn cần duyệt/từ chối
     * @param request DTO chứa status (ChapThuan/TuChoi) và adminNote
     * @return ClubApplicationResponse DTO chứa thông tin đơn sau khi review
     * @throws AppException APPLICATION_NOT_FOUND nếu không tìm thấy đơn
     * @throws AppException APPLICATION_ALREADY_REVIEWED nếu đơn đã được review
     * @throws AppException INVALID_APPLICATION_STATUS nếu status không hợp lệ
     * 
     * @Transactional Đảm bảo toàn bộ operation atomic (rollback nếu có lỗi)
     */
    @Transactional
    public ClubApplicationResponse reviewApplication(Integer requestId, ReviewApplicationRequest request) {
        // Lấy thông tin admin reviewer từ JWT token
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Users reviewer = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        // Tìm đơn yêu cầu theo requestId
        ClubApplications application = clubApplicationRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.APPLICATION_NOT_FOUND));
        
        // Kiểm tra đơn chỉ có thể review nếu đang ở trạng thái DangCho
        if (application.getStatus() != RequestStatus.DangCho) {
            throw new AppException(ErrorCode.APPLICATION_ALREADY_REVIEWED);
        }
        
        // Validate status chỉ cho phép ChapThuan hoặc TuChoi
        if (request.getStatus() != RequestStatus.ChapThuan && request.getStatus() != RequestStatus.TuChoi) {
            throw new AppException(ErrorCode.INVALID_APPLICATION_STATUS);
        }
        
        // Cập nhật thông tin review vào đơn
        application.setStatus(request.getStatus()); // ChapThuan hoặc TuChoi
        application.setAdminNote(request.getAdminNote()); // Ghi chú của admin (có thể null)
        application.setReviewer(reviewer); // Admin đã review đơn này
        application.setUpdatedAt(DateTimeUtils.nowVietnam()); // Thời gian review
        
        // Nếu duyệt (ChapThuan), tự động tạo CLB mới và setup
        if (request.getStatus() == RequestStatus.ChapThuan) {
            // 1. Tạo CLB mới từ thông tin đơn
            Clubs newClub = Clubs.builder()
                    .clubName(application.getProposedName()) // Tên CLB từ proposedName
                    .category(application.getCategory()) // Danh mục
                    .description(application.getDescription()) // Mô tả
                    .location(application.getLocation()) // Địa điểm
                    .email(application.getEmail()) // Email liên hệ
                    .founder(application.getCreator()) // Founder = người tạo đơn
                    .isActive(true) // CLB mới tạo luôn active
                    .build();
            
            newClub = clubRepository.save(newClub);
            application.setClub(newClub); // Link đơn với CLB đã tạo
            
            log.info("Club created: {} from application: {}", newClub.getClubId(), requestId);
            
            // 2. Tạo gói membership mặc định cho CLB
            // Gói này sẽ được dùng khi thành viên đăng ký tham gia CLB
            Memberships defaultPackage = Memberships.builder()
                    .club(newClub) // Thuộc về CLB vừa tạo
                    .packageName("Thành Viên Cơ Bản") // Tên gói mặc định
                    .term("1 tháng") // Thời hạn 1 tháng
                    .price(application.getDefaultMembershipFee()) // Giá từ đơn yêu cầu
                    .description("Gói thành viên mặc định được tạo tự động khi CLB được phê duyệt")
                    .isActive(true) // Gói active ngay
                    .build();
            
            defaultPackage = membershipRepository.save(defaultPackage);
            log.info("Default membership package created for club: {} with price: {}", 
                    newClub.getClubId(), application.getDefaultMembershipFee());
            
            // 3. Tự động thêm founder vào CLB với role ChuTich (Chủ tịch)
            LocalDateTime now = DateTimeUtils.nowVietnam();
            Registers founderRegistration = Registers.builder()
                    .user(application.getCreator()) // Founder = người tạo đơn
                    .membershipPackage(defaultPackage) // Gói membership mặc định
                    .status(JoinStatus.DaDuyet) // Đã duyệt (tự động duyệt cho founder)
                    .clubRole(ClubRoleType.ChuTich) // Founder tự động là Chủ tịch
                    .approver(reviewer) // Admin duyệt đơn cũng là người duyệt founder
                    .isPaid(true) // Miễn phí cho founder
                    .paymentMethod("MIỄN PHÍ - NGƯỜI SÁNG LẬP")
                    .paymentDate(now) // Ngày thanh toán = ngày tạo
                    .startDate(now) // Bắt đầu từ hôm nay
                    .endDate(now.plusYears(10)) // Kết thúc sau 10 năm (vĩnh viễn)
                    .joinDate(now) // Ngày tham gia
                    .createdAt(now) // Thời gian tạo record
                    .build();
            
            // Đảm bảo clubRole được set đúng trước khi save (phòng trường hợp builder không set)
            founderRegistration.setClubRole(ClubRoleType.ChuTich);
            founderRegistration = registerRepository.save(founderRegistration);
            
            // Verify sau khi save để đảm bảo clubRole được lưu đúng
            // (Có thể có vấn đề với JPA nếu không set đúng)
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
            // Lấy lại user từ database để đảm bảo có entity mới nhất (tránh stale entity)
            Users founder = userRepository.findById(application.getCreator().getUserId())
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            
            // Tìm role ChuTich trong database (role này phải được init trong ApplicationInitConfig)
            var chuTichRole = roleRepository.findByRoleName(RoleType.ChuTich)
                    .orElseThrow(() -> {
                        log.error("ChuTich role not found in database. Please check ApplicationInitConfig.");
                        return new RuntimeException("ChuTich role not found in database");
                    });
            
            // Cập nhật role của founder
            founder.setRole(chuTichRole);
            founder = userRepository.save(founder);
            userRepository.flush(); // Flush ngay để đảm bảo dữ liệu được ghi vào DB (tránh lazy loading issues)
            
            log.info("Founder {} role updated to ChuTich. Role ID: {}, Role Name: {}", 
                    founder.getEmail(), founder.getRole().getRoleId(), founder.getRole().getRoleName());
        }
        
        // Lưu đơn đã được review (cập nhật status, reviewer, adminNote, etc.)
        application = clubApplicationRepository.save(application);
        log.info("Application {} reviewed by {}: {}", requestId, reviewer.getEmail(), request.getStatus());
        
        // Chuyển đổi Entity sang DTO và trả về
        return clubApplicationMapper.toResponse(application);
    }
}
