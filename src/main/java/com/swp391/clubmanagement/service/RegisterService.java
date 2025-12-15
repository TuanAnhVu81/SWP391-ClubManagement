package com.swp391.clubmanagement.service;

import com.swp391.clubmanagement.dto.request.JoinClubRequest;
import com.swp391.clubmanagement.dto.response.RegisterResponse;
import com.swp391.clubmanagement.entity.Memberships;
import com.swp391.clubmanagement.entity.Registers;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.enums.JoinStatus;
import com.swp391.clubmanagement.enums.RoleType;
import com.swp391.clubmanagement.exception.AppException;
import com.swp391.clubmanagement.exception.ErrorCode;
import com.swp391.clubmanagement.mapper.RegisterMapper;
import com.swp391.clubmanagement.repository.MembershipRepository;
import com.swp391.clubmanagement.repository.RegisterRepository;
import com.swp391.clubmanagement.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class RegisterService {
    RegisterRepository registerRepository;
    MembershipRepository membershipRepository;
    UserRepository userRepository;
    RegisterMapper registerMapper;

    /**
     * Lấy user hiện tại từ SecurityContext
     */
    private Users getCurrentUser() {
        var context = SecurityContextHolder.getContext();
        String email = context.getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * Đăng ký tham gia CLB (mua gói package)
     * Trạng thái mặc định: ChoDuyet
     */
    public RegisterResponse joinClub(JoinClubRequest request) {
        Users currentUser = getCurrentUser();
        
        // Kiểm tra gói có tồn tại không
        Memberships membershipPackage = membershipRepository.findById(request.getPackageId())
                .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_NOT_FOUND));
        
        // Kiểm tra gói có đang active không
        if (!membershipPackage.getIsActive()) {
            throw new AppException(ErrorCode.PACKAGE_NOT_ACTIVE);
        }
        
        // Kiểm tra user đã đăng ký gói này chưa
        if (registerRepository.existsByUserAndMembershipPackage_PackageId(currentUser, request.getPackageId())) {
            throw new AppException(ErrorCode.ALREADY_REGISTERED);
        }
        
        // Kiểm tra user đã là thành viên CLB này chưa (đã duyệt + đã thanh toán)
        Integer clubId = membershipPackage.getClub().getClubId();
        if (registerRepository.existsByUserAndMembershipPackage_Club_ClubIdAndStatusAndIsPaid(
                currentUser, clubId, JoinStatus.DaDuyet, true)) {
            throw new AppException(ErrorCode.ALREADY_MEMBER);
        }
        
        // Tạo đăng ký mới
        Registers register = Registers.builder()
                .user(currentUser)
                .membershipPackage(membershipPackage)
                .status(JoinStatus.ChoDuyet)
                .joinReason(request.getJoinReason())
                .isPaid(false)
                .build();
        
        registerRepository.save(register);
        log.info("User {} registered for package {} (Club: {}) with reason: {}", 
                currentUser.getEmail(), membershipPackage.getPackageName(), 
                membershipPackage.getClub().getClubName(), request.getJoinReason());
        
        return registerMapper.toRegisterResponse(register);
    }

    /**
     * Xem danh sách các CLB mình đã đăng ký và trạng thái
     */
    public List<RegisterResponse> getMyRegistrations() {
        Users currentUser = getCurrentUser();
        List<Registers> registrations = registerRepository.findByUser(currentUser);
        return registerMapper.toRegisterResponseList(registrations);
    }

    /**
     * Xem chi tiết 1 đăng ký
     */
    public RegisterResponse getRegistrationById(Integer subscriptionId) {
        Users currentUser = getCurrentUser();
        
        Registers register = registerRepository.findById(subscriptionId)
                .orElseThrow(() -> new AppException(ErrorCode.REGISTER_NOT_FOUND));
        
        // Kiểm tra đăng ký này có phải của user hiện tại không
        if (!register.getUser().getUserId().equals(currentUser.getUserId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        
        return registerMapper.toRegisterResponse(register);
    }

    /**
     * Hủy đăng ký (chỉ khi trạng thái còn ChoDuyet)
     */
    public void cancelRegistration(Integer subscriptionId) {
        Users currentUser = getCurrentUser();
        
        Registers register = registerRepository.findById(subscriptionId)
                .orElseThrow(() -> new AppException(ErrorCode.REGISTER_NOT_FOUND));
        
        // Kiểm tra đăng ký này có phải của user hiện tại không
        if (!register.getUser().getUserId().equals(currentUser.getUserId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        
        // Chỉ được hủy khi còn ở trạng thái ChoDuyet
        if (register.getStatus() != JoinStatus.ChoDuyet) {
            throw new AppException(ErrorCode.INVALID_APPLICATION_STATUS);
        }
        
        registerRepository.delete(register);
        log.info("User {} canceled registration {}", currentUser.getEmail(), subscriptionId);
    }

    /**
     * Rời khỏi CLB (chỉ dành cho sinh viên, không dành cho ChuTich)
     * @param clubId ID của CLB muốn rời
     */
    public void leaveClub(Integer clubId) {
        Users currentUser = getCurrentUser();
        
        // Kiểm tra user có phải là ChuTich không
        if (currentUser.getRole().getRoleName() == RoleType.ChuTich) {
            throw new AppException(ErrorCode.PRESIDENT_CANNOT_LEAVE);
        }
        
        // Tìm registration của user trong CLB này
        Registers register = registerRepository.findByUserAndMembershipPackage_Club_ClubId(currentUser, clubId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_CLUB_MEMBER));
        
        // Kiểm tra phải là thành viên đang active (DaDuyet và đã thanh toán)
        if (register.getStatus() != JoinStatus.DaDuyet || !register.getIsPaid()) {
            throw new AppException(ErrorCode.NOT_ACTIVE_MEMBER);
        }
        
        // Set status thành DaRoiCLB (đã rời CLB)
        register.setStatus(JoinStatus.DaRoiCLB);
        registerRepository.save(register);
        
        log.info("User {} left club {} (subscriptionId: {})", 
                currentUser.getEmail(), clubId, register.getSubscriptionId());
    }
}

