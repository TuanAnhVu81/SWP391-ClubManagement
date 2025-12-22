package com.swp391.clubmanagement.service;

import com.swp391.clubmanagement.dto.request.ApproveRegisterRequest;
import com.swp391.clubmanagement.dto.request.ChangeRoleRequest;
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
import com.swp391.clubmanagement.repository.RoleRepository;
import com.swp391.clubmanagement.repository.UserRepository;
import com.swp391.clubmanagement.enums.RoleType;
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
import java.util.Optional;

/**
 * LeaderRegisterService - Service xử lý logic nghiệp vụ cho Leader quản lý đơn đăng ký tham gia CLB
 * 
 * Service này chịu trách nhiệm quản lý các đơn đăng ký tham gia CLB từ phía Leader:
 * - Xem danh sách đơn đăng ký vào CLB (tất cả trạng thái hoặc theo trạng thái)
 * - Duyệt/từ chối đơn đăng ký (ChoDuyet → DaDuyet/TuChoi)
 * - Xác nhận thanh toán (DaDuyet → isPaid = true, set startDate/endDate)
 * - Thay đổi role của thành viên (ChuTich, PhoChuTich, ThuKy, ThanhVien)
 * 
 * Business rules:
 * - Chỉ Leader (ChuTich, PhoChuTich) mới được quản lý đơn đăng ký
 * - Founder (người sáng lập CLB) cũng có quyền (tự động là ChuTich)
 * - Leader chỉ có thể quản lý đơn của CLB mà mình là Leader
 * 
 * Quy trình xử lý đơn đăng ký:
 * 1. Student đăng ký → status = ChoDuyet
 * 2. Leader duyệt → status = DaDuyet, set joinDate
 * 3. Student thanh toán → Leader xác nhận → isPaid = true, set paymentDate, startDate, endDate
 * 4. Student trở thành thành viên chính thức
 * 
 * @Service - Đánh dấu đây là một Spring Service, được quản lý bởi Spring Container
 * @RequiredArgsConstructor - Lombok tự động tạo constructor với các field final để dependency injection
 * @FieldDefaults - Lombok: tất cả field là PRIVATE và FINAL (immutable dependencies)
 * @Slf4j - Lombok: tự động tạo logger với tên "log" để ghi log
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class LeaderRegisterService {
    /**
     * Repository để truy vấn và thao tác với bảng Registers trong database
     */
    RegisterRepository registerRepository;
    
    /**
     * Repository để truy vấn và thao tác với bảng Users trong database
     * Dùng để lấy thông tin user hiện tại (leader)
     */
    UserRepository userRepository;
    
    /**
     * Mapper để chuyển đổi giữa Entity (Registers) và DTO (RegisterResponse)
     */
    RegisterMapper registerMapper;
    
    /**
     * Repository để truy vấn và thao tác với bảng Roles trong database
     * Dùng để cập nhật role của user khi thay đổi role trong CLB
     */
    RoleRepository roleRepository;

    /**
     * Danh sách các vai trò được phép duyệt đơn và quản lý CLB
     * Chỉ ChuTich và PhoChuTich mới có quyền này
     */
    private static final List<ClubRoleType> LEADER_ROLES = List.of(
            ClubRoleType.ChuTich,   // Chủ tịch (cao nhất)
            ClubRoleType.PhoChuTich // Phó chủ tịch
    );

    /**
     * Helper method: Lấy user hiện tại từ Security Context (JWT token)
     * 
     * Phương thức này được dùng bởi các method khác trong service để:
     * - Xác định Leader đang thực hiện request
     * - Đảm bảo Leader chỉ có thể quản lý CLB của mình
     * 
     * @return Users - Entity user hiện tại (Leader)
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user
     * 
     * Lưu ý: Method này chỉ hoạt động khi user đã đăng nhập (có valid JWT token)
     */
    private Users getCurrentUser() {
        // Lấy Security Context từ Spring Security
        var context = SecurityContextHolder.getContext();
        
        // Lấy email từ authentication (subject của JWT token)
        String email = context.getAuthentication().getName();
        
        // Tìm user trong database và return
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * Helper method: Kiểm tra user có phải là Leader của CLB không
     * 
     * Phương thức này kiểm tra xem user hiện tại có phải là Leader (ChuTich hoặc PhoChuTich)
     * của CLB cụ thể không. Được dùng để validate quyền trước khi thực hiện các thao tác.
     * 
     * Điều kiện để được coi là Leader:
     * - Có đơn đăng ký (Registers) trong CLB với clubRole = ChuTich hoặc PhoChuTich
     * - Status = DaDuyet (đã được duyệt)
     * - isPaid = true (đã thanh toán)
     * 
     * @param user - User cần kiểm tra
     * @param clubId - ID của CLB cần kiểm tra
     * @throws AppException với ErrorCode.NOT_CLUB_LEADER nếu user không phải Leader
     */
    private void validateLeaderRole(Users user, Integer clubId) {
        // Kiểm tra user có đơn đăng ký trong CLB với role Leader (ChuTich hoặc PhoChuTich)
        // và đã được duyệt (DaDuyet) + đã thanh toán (isPaid = true)
        boolean isLeader = registerRepository.existsByUserAndMembershipPackage_Club_ClubIdAndClubRoleInAndStatusAndIsPaid(
                user, clubId, LEADER_ROLES, JoinStatus.DaDuyet, true);
        
        // Nếu không phải Leader, throw exception
        if (!isLeader) {
            throw new AppException(ErrorCode.NOT_CLUB_LEADER);
        }
    }

    /**
     * Xem danh sách tất cả đơn đăng ký vào CLB (Leader only)
     * 
     * Phương thức này cho phép Leader xem tất cả các đơn đăng ký vào CLB của mình,
     * bao gồm tất cả các trạng thái: ChoDuyet, DaDuyet, TuChoi, DaRoiCLB, HetHan.
     * 
     * @param clubId - ID của CLB cần xem danh sách đơn đăng ký
     * @return List<RegisterResponse> - Danh sách đơn đăng ký đã được map sang DTO
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user (từ getCurrentUser)
     * @throws AppException với ErrorCode.NOT_CLUB_LEADER nếu user không phải Leader của CLB
     */
    public List<RegisterResponse> getClubRegistrations(Integer clubId) {
        Users currentUser = getCurrentUser();
        validateLeaderRole(currentUser, clubId);

        List<Registers> registrations = registerRepository.findByMembershipPackage_Club_ClubId(clubId);
        return registerMapper.toRegisterResponseList(registrations);
    }

    /**
     * Xem danh sách đơn đăng ký theo trạng thái (Leader only)
     * 
     * Phương thức này cho phép Leader xem các đơn đăng ký của CLB theo một trạng thái cụ thể.
     * Hữu ích khi Leader muốn xem chỉ các đơn đang chờ duyệt (ChoDuyet) hoặc đã duyệt (DaDuyet).
     * 
     * @param clubId - ID của CLB cần xem đơn đăng ký
     * @param status - Trạng thái để filter (ChoDuyet, DaDuyet, TuChoi, DaRoiCLB, HetHan)
     * @return List<RegisterResponse> - Danh sách đơn đăng ký với trạng thái đã chọn, đã được map sang DTO
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user (từ getCurrentUser)
     * @throws AppException với ErrorCode.NOT_CLUB_LEADER nếu user không phải Leader của CLB
     */
    public List<RegisterResponse> getClubRegistrationsByStatus(Integer clubId, JoinStatus status) {
        Users currentUser = getCurrentUser();
        validateLeaderRole(currentUser, clubId);

        List<Registers> registrations = registerRepository.findByMembershipPackage_Club_ClubIdAndStatus(clubId, status);
        return registerMapper.toRegisterResponseList(registrations);
    }

    /**
     * Duyệt hoặc từ chối đơn đăng ký tham gia CLB (Leader only)
     * 
     * Phương thức này cho phép Leader duyệt (approve) hoặc từ chối (reject) đơn đăng ký của Student.
     * 
     * Quy trình:
     * 1. Student đăng ký → status = ChoDuyet
     * 2. Leader duyệt → status = DaDuyet, set joinDate = now
     * 3. Leader từ chối → status = TuChoi
     * 
     * Business rules:
     * - Chỉ Leader (ChuTich, PhoChuTich) mới được duyệt/từ chối
     * - Chỉ có thể duyệt/từ chối đơn có status = ChoDuyet (đang chờ duyệt)
     * - Khi duyệt, set joinDate = thời gian hiện tại (timezone Việt Nam)
     * - Khi từ chối, không cần set joinDate
     * 
     * @param request - DTO chứa subscriptionId và status (DaDuyet hoặc TuChoi)
     * @return RegisterResponse - Thông tin đơn sau khi được duyệt/từ chối, đã được map sang DTO
     * @throws AppException với ErrorCode.REGISTER_NOT_FOUND nếu không tìm thấy đơn
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user (từ getCurrentUser)
     * @throws AppException với ErrorCode.NOT_CLUB_LEADER nếu user không phải Leader của CLB
     * @throws AppException với ErrorCode.APPLICATION_ALREADY_REVIEWED nếu đơn đã được duyệt/từ chối
     * @throws AppException với ErrorCode.INVALID_APPLICATION_STATUS nếu status không phải DaDuyet hoặc TuChoi
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
     * Xác nhận sinh viên đã đóng tiền (Leader only)
     * 
     * Phương thức này cho phép Leader xác nhận khi Student đã thanh toán phí membership.
     * Sau khi xác nhận, Student sẽ trở thành thành viên chính thức của CLB.
     * 
     * Quy trình:
     * 1. Student được duyệt → status = DaDuyet
     * 2. Student thanh toán (qua PayOS hoặc trực tiếp)
     * 3. Leader xác nhận thanh toán → isPaid = true, set paymentDate, startDate, endDate
     * 4. Student trở thành thành viên chính thức
     * 
     * Business rules:
     * - Chỉ Leader (ChuTich, PhoChuTich) mới được xác nhận thanh toán
     * - Chỉ có thể xác nhận đơn có status = DaDuyet (đã được duyệt)
     * - Không thể xác nhận lại nếu đã thanh toán (isPaid = true)
     * - startDate = thời gian hiện tại
     * - endDate = startDate + term của gói membership (tính toán dựa trên term string)
     * 
     * @param request - DTO chứa subscriptionId và paymentMethod (phương thức thanh toán)
     * @return RegisterResponse - Thông tin đơn sau khi xác nhận thanh toán, đã được map sang DTO
     * @throws AppException với ErrorCode.REGISTER_NOT_FOUND nếu không tìm thấy đơn
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user (từ getCurrentUser)
     * @throws AppException với ErrorCode.NOT_CLUB_LEADER nếu user không phải Leader của CLB
     * @throws AppException với ErrorCode.INVALID_APPLICATION_STATUS nếu status không phải DaDuyet
     * @throws AppException với ErrorCode.APPLICATION_ALREADY_REVIEWED nếu đã thanh toán (isPaid = true)
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

        return registerMapper.toRegisterResponse(register);
    }
    
    /**
     * Tính toán endDate (ngày hết hạn) dựa trên term (kỳ hạn) của gói membership
     * 
     * Helper method này parse string term và tính toán ngày hết hạn membership.
     * Hỗ trợ nhiều format khác nhau cho term.
     * 
     * Format được hỗ trợ:
     * - Tiếng Việt: "1 tháng", "3 tháng", "6 tháng", "1 năm", "2 năm"...
     * - Tiếng Anh: "1 month", "6 months", "1 year", "2 years"...
     * 
     * Logic:
     * - Parse số từ đầu string (ví dụ: "3 tháng" → 3)
     * - Xác định đơn vị (tháng/năm hoặc month/year)
     * - Tính endDate = startDate + số lượng đơn vị
     * - Nếu không parse được, sử dụng giá trị mặc định (6 tháng cho tháng, 1 năm cho năm)
     * 
     * @param startDate - Ngày bắt đầu membership (thường là thời gian hiện tại)
     * @param term - Kỳ hạn dạng string (ví dụ: "1 tháng", "3 tháng", "1 năm")
     * @return LocalDateTime - Ngày hết hạn membership
     * 
     * Lưu ý: Method này có error handling tốt, luôn trả về một giá trị hợp lệ
     *        (sử dụng giá trị mặc định nếu parse thất bại)
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
     * Thay đổi role (vai trò) của thành viên trong CLB (Leader only)
     * 
     * Phương thức này cho phép Leader thăng chức hoặc hạ chức thành viên trong CLB.
     * Có thể thay đổi giữa các role: ChuTich, PhoChuTich, ThuKy, ThanhVien.
     * 
     * Business rules quan trọng:
     * - Chỉ Leader (ChuTich, PhoChuTich) mới được thay đổi role
     * - Thành viên phải đã được duyệt và đã thanh toán (status = DaDuyet, isPaid = true)
     * - Khi thay đổi role, có thể ảnh hưởng đến Role tổng thể của user trong hệ thống
     * 
     * Logic đặc biệt về Role (trong bảng Users):
     * 1. Nếu ClubRole cũ = ChuTich và Role = ChuTich, khi đổi sang ClubRole khác:
     *    → Role trong bảng Users sẽ chuyển về SinhVien
     * 
     * 2. Nếu ClubRole mới = ChuTich (thăng chức lên Chủ tịch):
     *    → Role trong bảng Users sẽ chuyển thành ChuTich
     *    → ChuTich hiện tại (nếu có) sẽ tự động bị hạ xuống ThanhVien (ClubRole) và SinhVien (Role)
     *    → Đảm bảo chỉ có 1 ChuTich trong CLB tại một thời điểm
     * 
     * @param clubId - ID của CLB
     * @param userId - ID của user cần thay đổi role
     * @param request - DTO chứa newRole (ClubRoleType mới)
     * @return RegisterResponse - Thông tin đơn sau khi thay đổi role, đã được map sang DTO
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user
     * @throws AppException với ErrorCode.REGISTER_NOT_FOUND nếu không tìm thấy đơn đăng ký
     * @throws AppException với ErrorCode.NOT_CLUB_LEADER nếu user hiện tại không phải Leader
     * @throws AppException với ErrorCode.INVALID_APPLICATION_STATUS nếu thành viên chưa active
     * 
     * @Transactional - Rất quan trọng: Toàn bộ operations (update register, update user role, 
     *                  auto-demote current ChuTich) phải được thực hiện trong một transaction
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
     * Xóa thành viên khỏi CLB (Kick member) - Leader only
     * 
     * Phương thức này cho phép Leader xóa (kick) một thành viên khỏi CLB.
     * Khác với leaveClub() (thành viên tự rời), đây là hành động Leader xóa thành viên.
     * 
     * Business rules:
     * - Chỉ Leader (ChuTich, PhoChuTich) mới được kick thành viên
     * - Chỉ có thể kick thành viên đang active (status = DaDuyet)
     * - Sau khi kick, status sẽ được set thành DaRoiCLB (đã rời CLB)
     * - Đơn đăng ký vẫn được giữ lại trong database (không xóa) để lưu lịch sử
     * 
     * Use cases:
     * - Thành viên vi phạm quy định CLB
     * - Thành viên không tham gia hoạt động
     * - Leader muốn loại bỏ thành viên vì lý do nào đó
     * 
     * @param clubId - ID của CLB
     * @param userId - ID của user cần kick
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user
     * @throws AppException với ErrorCode.REGISTER_NOT_FOUND nếu không tìm thấy đơn đăng ký
     * @throws AppException với ErrorCode.NOT_CLUB_LEADER nếu user hiện tại không phải Leader
     * @throws AppException với ErrorCode.INVALID_APPLICATION_STATUS nếu thành viên không active
     * 
     * @Transactional - Đảm bảo toàn bộ operations được thực hiện trong một transaction
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

