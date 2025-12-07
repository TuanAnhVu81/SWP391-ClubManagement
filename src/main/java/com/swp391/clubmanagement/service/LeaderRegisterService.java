package com.swp391.clubmanagement.service;

import com.swp391.clubmanagement.dto.request.ApproveRegisterRequest;
import com.swp391.clubmanagement.dto.request.ConfirmPaymentRequest;
import com.swp391.clubmanagement.dto.response.RegisterResponse;
import com.swp391.clubmanagement.entity.Registers;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.enums.ClubRoleType;
import com.swp391.clubmanagement.enums.JoinStatus;
import com.swp391.clubmanagement.exception.AppException;
import com.swp391.clubmanagement.exception.ErrorCode;
import com.swp391.clubmanagement.mapper.RegisterMapper;
import com.swp391.clubmanagement.repository.RegisterRepository;
import com.swp391.clubmanagement.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class LeaderRegisterService {
    RegisterRepository registerRepository;
    UserRepository userRepository;
    RegisterMapper registerMapper;

    // Các vai trò được phép duyệt đơn
    private static final List<ClubRoleType> LEADER_ROLES = List.of(
            ClubRoleType.ChuTich, 
            ClubRoleType.PhoChuTich
    );

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
     * Kiểm tra user có phải là Leader của CLB không
     */
    private void validateLeaderRole(Users user, Integer clubId) {
        boolean isLeader = registerRepository.existsByUserAndMembershipPackage_Club_ClubIdAndClubRoleInAndStatusAndIsPaid(
                user, clubId, LEADER_ROLES, JoinStatus.DaDuyet, true);
        
        if (!isLeader) {
            throw new AppException(ErrorCode.NOT_CLUB_LEADER);
        }
    }

    /**
     * Xem danh sách đơn đăng ký vào CLB mình (tất cả trạng thái)
     */
    public List<RegisterResponse> getClubRegistrations(Integer clubId) {
        Users currentUser = getCurrentUser();
        validateLeaderRole(currentUser, clubId);

        List<Registers> registrations = registerRepository.findByMembershipPackage_Club_ClubId(clubId);
        return registerMapper.toRegisterResponseList(registrations);
    }

    /**
     * Xem danh sách đơn đăng ký theo trạng thái (ChoDuyet, DaDuyet, TuChoi...)
     */
    public List<RegisterResponse> getClubRegistrationsByStatus(Integer clubId, JoinStatus status) {
        Users currentUser = getCurrentUser();
        validateLeaderRole(currentUser, clubId);

        List<Registers> registrations = registerRepository.findByMembershipPackage_Club_ClubIdAndStatus(clubId, status);
        return registerMapper.toRegisterResponseList(registrations);
    }

    /**
     * Duyệt đơn (DaDuyet) hoặc Từ chối (TuChoi)
     */
    public RegisterResponse approveRegistration(ApproveRegisterRequest request) {
        Users currentUser = getCurrentUser();

        // Lấy đơn đăng ký
        Registers register = registerRepository.findById(request.getSubscriptionId())
                .orElseThrow(() -> new AppException(ErrorCode.REGISTER_NOT_FOUND));

        Integer clubId = register.getMembershipPackage().getClub().getClubId();
        
        // Kiểm tra quyền Leader
        validateLeaderRole(currentUser, clubId);

        // Kiểm tra trạng thái hiện tại phải là ChoDuyet
        if (register.getStatus() != JoinStatus.ChoDuyet) {
            throw new AppException(ErrorCode.APPLICATION_ALREADY_REVIEWED);
        }

        // Kiểm tra status request hợp lệ (chỉ cho DaDuyet hoặc TuChoi)
        if (request.getStatus() != JoinStatus.DaDuyet && request.getStatus() != JoinStatus.TuChoi) {
            throw new AppException(ErrorCode.INVALID_APPLICATION_STATUS);
        }

        // Cập nhật trạng thái
        register.setStatus(request.getStatus());
        register.setApprover(currentUser);

        // Nếu duyệt, set joinDate
        if (request.getStatus() == JoinStatus.DaDuyet) {
            register.setJoinDate(LocalDateTime.now());
            log.info("Registration {} approved by Leader {}", request.getSubscriptionId(), currentUser.getEmail());
        } else {
            log.info("Registration {} rejected by Leader {}", request.getSubscriptionId(), currentUser.getEmail());
        }

        registerRepository.save(register);
        return registerMapper.toRegisterResponse(register);
    }

    /**
     * Xác nhận sinh viên đã đóng tiền
     */
    public RegisterResponse confirmPayment(ConfirmPaymentRequest request) {
        Users currentUser = getCurrentUser();

        // Lấy đơn đăng ký
        Registers register = registerRepository.findById(request.getSubscriptionId())
                .orElseThrow(() -> new AppException(ErrorCode.REGISTER_NOT_FOUND));

        Integer clubId = register.getMembershipPackage().getClub().getClubId();
        
        // Kiểm tra quyền Leader
        validateLeaderRole(currentUser, clubId);

        // Kiểm tra đơn đã được duyệt chưa
        if (register.getStatus() != JoinStatus.DaDuyet) {
            throw new AppException(ErrorCode.INVALID_APPLICATION_STATUS);
        }

        // Kiểm tra đã thanh toán chưa
        if (register.getIsPaid()) {
            throw new AppException(ErrorCode.APPLICATION_ALREADY_REVIEWED);
        }

        // Xác nhận thanh toán
        register.setIsPaid(true);
        register.setPaymentDate(LocalDateTime.now());
        register.setPaymentMethod(request.getPaymentMethod());
        
        // Set thời gian bắt đầu và kết thúc membership (ví dụ: 6 tháng)
        register.setStartDate(LocalDateTime.now());
        register.setEndDate(LocalDateTime.now().plusMonths(6)); // Có thể lấy từ package term

        registerRepository.save(register);
        log.info("Payment confirmed for registration {} by Leader {}", request.getSubscriptionId(), currentUser.getEmail());

        return registerMapper.toRegisterResponse(register);
    }
}

