// Package định nghĩa service layer - xử lý business logic cho Leader quản lý đăng ký
package com.swp391.clubmanagement.service;

// ========== DTO ==========
import com.swp391.clubmanagement.dto.request.ApproveRegisterRequest; // Request duyệt/từ chối đơn
import com.swp391.clubmanagement.dto.request.ChangeRoleRequest; // Request thay đổi vai trò thành viên
import com.swp391.clubmanagement.dto.request.ConfirmPaymentRequest; // Request xác nhận thanh toán
import com.swp391.clubmanagement.dto.response.RegisterResponse; // Response thông tin đăng ký

// ========== Entity ==========
import com.swp391.clubmanagement.entity.Registers; // Entity đăng ký tham gia CLB
import com.swp391.clubmanagement.entity.Users; // Entity người dùng

// ========== Enum ==========
import com.swp391.clubmanagement.enums.ClubRoleType; // Vai trò trong CLB
import com.swp391.clubmanagement.enums.JoinStatus; // Trạng thái tham gia
import com.swp391.clubmanagement.enums.RoleType; // Vai trò hệ thống

// ========== Exception ==========
import com.swp391.clubmanagement.exception.AppException; // Custom exception
import com.swp391.clubmanagement.exception.ErrorCode; // Mã lỗi hệ thống

// ========== Mapper ==========
import com.swp391.clubmanagement.mapper.RegisterMapper; // Chuyển đổi Entity <-> DTO

// ========== Repository ==========
import com.swp391.clubmanagement.repository.RegisterRepository; // Repository cho bảng Registers
import com.swp391.clubmanagement.repository.RoleRepository; // Repository cho bảng Roles
import com.swp391.clubmanagement.repository.UserRepository; // Repository cho bảng Users

// ========== Utilities ==========
import com.swp391.clubmanagement.utils.DateTimeUtils; // Xử lý thời gian theo múi giờ VN

// ========== Lombok ==========
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor; // Tự động tạo constructor inject dependencies
import lombok.experimental.FieldDefaults; // Tự động thêm private final cho fields
import lombok.extern.slf4j.Slf4j; // Tự động tạo logger

// ========== Spring Framework ==========
import org.springframework.security.core.context.SecurityContextHolder; // Lấy user hiện tại từ JWT
import org.springframework.stereotype.Service; // Đánh dấu class là Spring Service Bean
import org.springframework.transaction.annotation.Transactional; // Quản lý transaction

// ========== Java Standard Library ==========
import java.time.LocalDateTime; // Ngày giờ
import java.util.List; // Danh sách
import java.util.Optional; // Optional

/**
 * Service quản lý đăng ký tham gia CLB (dành cho Leader)
 * 
 * Chức năng chính:
 * - Xem danh sách đơn đăng ký vào CLB (tất cả trạng thái hoặc theo status)
 * - Duyệt/từ chối đơn đăng ký
 * - Xác nhận thanh toán (khi thành viên đã đóng phí)
 * - Thay đổi vai trò thành viên (thăng chức/hạ chức)
 * - Xóa thành viên khỏi CLB (kick)
 * 
 * Business Rules:
 * - Chỉ Leader (ChuTich, PhoChuTich) mới được thực hiện các chức năng này
 * - Khi thay đổi role thành ChuTich, tự động cập nhật Role trong bảng Users
 * - Khi ChuTich thăng người khác thành ChuTich, tự động hạ mình xuống ThanhVien
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
public class LeaderRegisterService {
    /** Repository thao tác với bảng registers */
    RegisterRepository registerRepository;
    
    /** Repository thao tác với bảng users */
    UserRepository userRepository;
    
    /** Mapper chuyển đổi Entity (Registers) <-> DTO (RegisterResponse) */
    RegisterMapper registerMapper;
    
    /** Repository thao tác với bảng roles (để cập nhật Role khi thay đổi ClubRole) */
    RoleRepository roleRepository;
    
    /** Service tạo payment history khi xác nhận thanh toán */
    PaymentHistoryService paymentHistoryService;

    /** Các vai trò được phép duyệt đơn (ChuTich, PhoChuTich) */
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
            register.setJoinDate(DateTimeUtils.nowVietnam());
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
        register.setPaymentDate(DateTimeUtils.nowVietnam());
        register.setPaymentMethod(request.getPaymentMethod());
        
        // Set thời gian bắt đầu và kết thúc membership dựa trên term của gói
        LocalDateTime startDate = DateTimeUtils.nowVietnam();
        register.setStartDate(startDate);
        
        // Tính endDate dựa trên term của package
        String term = register.getMembershipPackage().getTerm();
        LocalDateTime endDate = calculateEndDate(startDate, term);
        register.setEndDate(endDate);

        registerRepository.save(register);
        log.info("Payment confirmed for registration {} by Leader {}. Membership valid until: {}", 
                request.getSubscriptionId(), currentUser.getEmail(), endDate);

        // Tạo payment history record
        paymentHistoryService.createPaymentHistory(register);
        log.info("Payment history created for registration {}", request.getSubscriptionId());

        return registerMapper.toRegisterResponse(register);
    }
    
    /**
     * Tính toán endDate dựa trên term của gói membership
     * @param startDate Ngày bắt đầu
     * @param term Kỳ hạn (VD: "1 tháng", "3 tháng", "6 tháng", "1 năm")
     * @return Ngày hết hạn
     */
    private LocalDateTime calculateEndDate(LocalDateTime startDate, String term) {
        if (term == null || term.isEmpty()) {
            // Mặc định 1 năm nếu không có term
            return startDate.plusYears(1);
        }
        
        // Chuyển về lowercase và trim để dễ xử lý
        String normalizedTerm = term.toLowerCase().trim();
        
        // Parse term và tính endDate
        if (normalizedTerm.contains("tháng")) {
            // Trích xuất số tháng (VD: "1 tháng", "3 tháng", "6 tháng")
            try {
                String[] parts = normalizedTerm.split("\\s+");
                int months = Integer.parseInt(parts[0]);
                return startDate.plusMonths(months);
            } catch (Exception e) {
                log.warn("Cannot parse term: {}. Using default 6 months", term);
                return startDate.plusMonths(6);
            }
        } else if (normalizedTerm.contains("năm")) {
            // Trích xuất số năm (VD: "1 năm", "2 năm")
            try {
                String[] parts = normalizedTerm.split("\\s+");
                int years = Integer.parseInt(parts[0]);
                return startDate.plusYears(years);
            } catch (Exception e) {
                log.warn("Cannot parse term: {}. Using default 1 year", term);
                return startDate.plusYears(1);
            }
        } else if (normalizedTerm.contains("month")) {
            // Support English format (VD: "1 month", "6 months")
            try {
                String[] parts = normalizedTerm.split("\\s+");
                int months = Integer.parseInt(parts[0]);
                return startDate.plusMonths(months);
            } catch (Exception e) {
                log.warn("Cannot parse term: {}. Using default 6 months", term);
                return startDate.plusMonths(6);
            }
        } else if (normalizedTerm.contains("year")) {
            // Support English format (VD: "1 year", "2 years")
            try {
                String[] parts = normalizedTerm.split("\\s+");
                int years = Integer.parseInt(parts[0]);
                return startDate.plusYears(years);
            } catch (Exception e) {
                log.warn("Cannot parse term: {}. Using default 1 year", term);
                return startDate.plusYears(1);
            }
        } else {
            // Format không nhận diện được, mặc định 1 năm
            log.warn("Unknown term format: {}. Using default 1 year", term);
            return startDate.plusYears(1);
        }
    }
    
    /**
     * Thăng chức/Hạ chức thành viên (Thay đổi role)
     * VD: Từ ThanhVien lên PhoChuTich, hoặc từ PhoChuTich xuống ThanhVien
     */
    @Transactional
    public RegisterResponse changeRole(Integer clubId, String userId, ChangeRoleRequest request) {
        Users currentUser = getCurrentUser();
        
        // Kiểm tra quyền Leader
        validateLeaderRole(currentUser, clubId);
        
        // Lấy user cần thay đổi role
        Users userToChange = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        // Lấy đơn đăng ký của user trong club
        Registers register = registerRepository.findByUserAndMembershipPackage_Club_ClubId(userToChange, clubId)
                .orElseThrow(() -> new AppException(ErrorCode.REGISTER_NOT_FOUND));
        
        // Kiểm tra thành viên phải đã được duyệt và đã đóng phí
        if (register.getStatus() != JoinStatus.DaDuyet || !register.getIsPaid()) {
            throw new AppException(ErrorCode.INVALID_APPLICATION_STATUS);
        }
        
        // Lưu role cũ để log
        ClubRoleType oldClubRole = register.getClubRole();
        RoleType currentUserRole = userToChange.getRole().getRoleName();
        
        // Cập nhật role mới
        register.setClubRole(request.getNewRole());
        registerRepository.save(register);
        
        // Logic 1: Nếu ClubRole cũ = ChuTich và Role = ChuTich, khi đổi sang ClubRole khác thì Role -> SinhVien
        if (oldClubRole == ClubRoleType.ChuTich 
                && currentUserRole == RoleType.ChuTich 
                && request.getNewRole() != ClubRoleType.ChuTich) {
            var sinhVienRole = roleRepository.findByRoleName(RoleType.SinhVien)
                    .orElseThrow(() -> {
                        log.error("SinhVien role not found in database. Please check ApplicationInitConfig.");
                        return new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
                    });
            userToChange.setRole(sinhVienRole);
            userRepository.save(userToChange);
            log.info("User {} role changed from ChuTich to SinhVien because ClubRole changed from ChuTich to {}", 
                    userToChange.getEmail(), request.getNewRole());
        }
        
        // Logic 2: Nếu ClubRole cũ != ChuTich và ClubRole mới = ChuTich thì Role -> ChuTich
        if (oldClubRole != ClubRoleType.ChuTich && request.getNewRole() == ClubRoleType.ChuTich) {
            var chuTichRole = roleRepository.findByRoleName(RoleType.ChuTich)
                    .orElseThrow(() -> {
                        log.error("ChuTich role not found in database. Please check ApplicationInitConfig.");
                        return new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
                    });
            userToChange.setRole(chuTichRole);
            userRepository.save(userToChange);
            log.info("User {} role changed to ChuTich because ClubRole changed from {} to ChuTich", 
                    userToChange.getEmail(), oldClubRole);
            
            // Logic 3: Khi ChuTich đổi role người khác thành ChuTich, thì ChuTich hiện tại sẽ bị tự động đổi
            // ClubRole thành ThanhVien và Role thành SinhVien
            Optional<Registers> currentUserRegister = registerRepository.findByUserAndMembershipPackage_Club_ClubId(currentUser, clubId);
            if (currentUserRegister.isPresent()) {
                Registers currentUserReg = currentUserRegister.get();
                // Kiểm tra currentUser có phải là ChuTich không
                if (currentUserReg.getClubRole() == ClubRoleType.ChuTich 
                        && currentUserReg.getStatus() == JoinStatus.DaDuyet 
                        && currentUserReg.getIsPaid()) {
                    // Hạ currentUser xuống ThanhVien
                    currentUserReg.setClubRole(ClubRoleType.ThanhVien);
                    registerRepository.save(currentUserReg);
                    
                    // Đổi Role của currentUser thành SinhVien
                    var sinhVienRole = roleRepository.findByRoleName(RoleType.SinhVien)
                            .orElseThrow(() -> {
                                log.error("SinhVien role not found in database. Please check ApplicationInitConfig.");
                                return new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
                            });
                    currentUser.setRole(sinhVienRole);
                    userRepository.save(currentUser);
                    
                    log.info("Current ChuTich {} automatically demoted to ThanhVien (ClubRole) and SinhVien (Role) " +
                            "because they promoted {} to ChuTich", 
                            currentUser.getEmail(), userToChange.getEmail());
                }
            }
        }

        log.info("Member role changed: userId={}, user={}, clubId={}, oldClubRole={}, newClubRole={}, by={}", 
                userId, userToChange.getEmail(), clubId, oldClubRole, request.getNewRole(), currentUser.getEmail());

        return registerMapper.toRegisterResponse(register);
    }
    
    /**
     * Xóa thành viên khỏi CLB (Kick)
     * Set status = DaRoiCLB để đánh dấu đã rời khỏi CLB
     */
    @Transactional
    public void kickMember(Integer clubId, String userId) {
        Users currentUser = getCurrentUser();
        
        // Kiểm tra quyền Leader
        validateLeaderRole(currentUser, clubId);
        
        // Lấy user cần xóa
        Users userToKick = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        // Lấy đơn đăng ký của user trong club
        Registers register = registerRepository.findByUserAndMembershipPackage_Club_ClubId(userToKick, clubId)
                .orElseThrow(() -> new AppException(ErrorCode.REGISTER_NOT_FOUND));
        
        // Kiểm tra thành viên phải đang active (DaDuyet)
        if (register.getStatus() != JoinStatus.DaDuyet) {
            throw new AppException(ErrorCode.INVALID_APPLICATION_STATUS);
        }
        
        // Set status = DaRoiCLB (Đã rời CLB)
        register.setStatus(JoinStatus.DaRoiCLB);
        
        registerRepository.save(register);
        log.info("Member kicked from club: userId={}, user={}, clubId={}, by={}", 
                userId, userToKick.getEmail(), clubId, currentUser.getEmail());
    }
}

