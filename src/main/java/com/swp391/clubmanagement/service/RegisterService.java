package com.swp391.clubmanagement.service;

import com.swp391.clubmanagement.dto.request.JoinClubRequest;
import com.swp391.clubmanagement.dto.request.RenewMembershipRequest;
import com.swp391.clubmanagement.dto.response.RegisterResponse;
import com.swp391.clubmanagement.entity.Memberships;
import com.swp391.clubmanagement.entity.Registers;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.enums.ClubRoleType;
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

import java.time.LocalDateTime;
import java.util.List;

/**
 * RegisterService - Service xử lý logic nghiệp vụ cho đăng ký tham gia CLB
 * 
 * Service này chịu trách nhiệm quản lý toàn bộ vòng đời của đơn đăng ký tham gia CLB:
 * - Đăng ký tham gia CLB (mua gói membership) - Student
 * - Xem danh sách đơn đăng ký của mình - Student
 * - Xem chi tiết 1 đơn đăng ký - Student/Leader
 * - Hủy đăng ký (khi còn ở trạng thái ChoDuyet) - Student
 * - Rời khỏi CLB (khi đã là thành viên) - Student (trừ ChuTich)
 * - Gia hạn membership (khi hết hạn) - Student
 * 
 * Quy trình đăng ký tham gia CLB:
 * 1. Student chọn gói membership → joinClub() → Tạo đơn với status = ChoDuyet
 * 2. Leader duyệt đơn → approveRegistration() (LeaderRegisterService) → status = DaDuyet
 * 3. Student thanh toán → confirmPayment() → isPaid = true, startDate/endDate được set
 * 4. Khi hết hạn → Tự động update status = HetHan (lazy evaluation)
 * 5. Student có thể gia hạn → renewMembership() → Tạo đơn mới
 * 
 * Business rules quan trọng:
 * - Một user chỉ có thể là thành viên của 1 CLB tại một thời điểm (khi tạo đơn thành lập CLB)
 * - ChuTich không thể rời CLB (phải chuyển quyền trước)
 * - Chỉ được gia hạn khi status = HetHan
 * - Có thể tái gia nhập sau khi status = TuChoi, DaRoiCLB, hoặc HetHan
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
public class RegisterService {
    /**
     * Repository để truy vấn và thao tác với bảng Registers trong database
     * Chứa thông tin đăng ký tham gia CLB của các user
     */
    RegisterRepository registerRepository;
    
    /**
     * Repository để truy vấn và thao tác với bảng Memberships trong database
     * Chứa các gói membership của các CLB
     */
    MembershipRepository membershipRepository;
    
    /**
     * Repository để truy vấn và thao tác với bảng Users trong database
     * Dùng để lấy thông tin user hiện tại
     */
    UserRepository userRepository;
    
    /**
     * Mapper để chuyển đổi giữa Entity (Registers) và DTO (RegisterResponse)
     */
    RegisterMapper registerMapper;

    /**
     * Helper method: Lấy user hiện tại từ Security Context (JWT token)
     * 
     * Phương thức này được dùng bởi các method khác trong service để:
     * - Xác định user đang thực hiện request
     * - Đảm bảo user chỉ có thể thao tác với đơn đăng ký của chính mình (trừ Leader)
     * 
     * @return Users - Entity user hiện tại
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
     * Helper: Kiểm tra và tự động cập nhật status nếu membership đã hết hạn
     * (Lazy Evaluation - chỉ check khi cần)
     * 
     * Phương thức này được gọi khi cần kiểm tra membership có hết hạn không.
     * Thay vì dùng scheduled job để check định kỳ, ta check khi user request data (lazy evaluation).
     * 
     * Logic:
     * - Chỉ check các register có: status = DaDuyet, isPaid = true, có endDate
     * - Nếu endDate < now → Update status thành HetHan
     * - Ghi log chi tiết để tracking
     * 
     * Ưu điểm của lazy evaluation:
     * - Không cần chạy scheduled job tốn tài nguyên
     * - Chỉ update khi thực sự cần thiết (khi user xem data)
     * - Đảm bảo data được update đúng lúc user cần
     * 
     * @param register - Register cần kiểm tra
     * @return true nếu đã update status thành HetHan, false nếu chưa hết hạn hoặc không cần update
     */
    private boolean checkAndUpdateExpiry(Registers register) {
        // Điều kiện để kiểm tra expiry:
        // 1. status = DaDuyet (đã được duyệt)
        // 2. isPaid = true (đã thanh toán)
        // 3. có endDate (có thời hạn)
        // 4. endDate < now (đã hết hạn)
        if (register.getStatus() == JoinStatus.DaDuyet 
            && register.getIsPaid() != null && register.getIsPaid()
            && register.getEndDate() != null
            && register.getEndDate().isBefore(LocalDateTime.now())) {
            
            // Membership đã hết hạn → Update status thành HetHan
            register.setStatus(JoinStatus.HetHan);
            registerRepository.save(register);
            
            // Ghi log để tracking và debugging
            log.info("Auto-updated subscription {} to HetHan. User: {}, Club: {}, EndDate: {}", 
                    register.getSubscriptionId(),
                    register.getUser().getEmail(),
                    register.getMembershipPackage().getClub().getClubName(),
                    register.getEndDate());
            
            return true;  // Đã update
        }
        return false;  // Chưa hết hạn hoặc không cần update
    }

    /**
     * Helper: Kiểm tra và update expiry cho danh sách registers
     * 
     * @param registers Danh sách cần kiểm tra
     */
    private void checkAndUpdateExpiryBatch(List<Registers> registers) {
        int updatedCount = 0;
        for (Registers register : registers) {
            if (checkAndUpdateExpiry(register)) {
                updatedCount++;
            }
        }
        if (updatedCount > 0) {
            log.info("Auto-updated {} expired memberships to HetHan", updatedCount);
        }
    }

    /**
     * Đăng ký tham gia CLB (mua gói membership package)
     * 
     * Phương thức này cho phép Student đăng ký tham gia một CLB bằng cách chọn gói membership.
     * Sau khi đăng ký, đơn sẽ ở trạng thái ChoDuyet (chờ Leader duyệt).
     * 
     * Quy trình:
     * 1. Student chọn gói membership từ CLB
     * 2. Gọi joinClub() → Tạo đơn đăng ký với status = ChoDuyet
     * 3. Leader duyệt đơn (LeaderRegisterService) → status = DaDuyet
     * 4. Student thanh toán → isPaid = true, startDate/endDate được set
     * 5. Student trở thành thành viên chính thức
     * 
     * Logic xử lý tái gia nhập (re-apply):
     * - Nếu chưa có registration trong CLB này → Tạo mới
     * - Nếu status = ChoDuyet → Không cho đăng ký lại (đang chờ duyệt)
     * - Nếu status = DaDuyet + isPaid = true → Không cho đăng ký lại (đã là thành viên active)
     * - Nếu status = TuChoi, DaRoiCLB, hoặc HetHan → CHO PHÉP đăng ký lại (xóa đơn cũ, tạo mới)
     * 
     * @param request - DTO chứa packageId (ID gói membership) và joinReason (lý do tham gia)
     * @return RegisterResponse - Thông tin đơn đăng ký đã được tạo (với status = ChoDuyet)
     * @throws AppException với ErrorCode.PACKAGE_NOT_FOUND nếu không tìm thấy gói
     * @throws AppException với ErrorCode.PACKAGE_NOT_ACTIVE nếu gói không active
     * @throws AppException với ErrorCode.ALREADY_REGISTERED nếu đang chờ duyệt
     * @throws AppException với ErrorCode.ALREADY_MEMBER nếu đã là thành viên active
     * 
     * Lưu ý:
     * - User chỉ có thể đăng ký khi đã đăng nhập (xác định từ JWT token)
     * - Đơn mới tạo sẽ có status = ChoDuyet, cần Leader duyệt
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
        
        // Lấy club ID
        Integer clubId = membershipPackage.getClub().getClubId();
        
        // Tìm registration cũ của user trong CLB này (nếu có)
        var existingRegister = registerRepository.findByUserAndMembershipPackage_Club_ClubId(currentUser, clubId);
        
        if (existingRegister.isPresent()) {
            Registers oldRegister = existingRegister.get();
            
            // Lazy evaluation: Check và update expiry trước khi validate
            checkAndUpdateExpiry(oldRegister);
            
            JoinStatus oldStatus = oldRegister.getStatus();
            
            // Nếu đang chờ duyệt -> không cho đăng ký lại
            if (oldStatus == JoinStatus.ChoDuyet) {
                throw new AppException(ErrorCode.ALREADY_REGISTERED);
            }
            
            // Nếu đã là thành viên (đã duyệt + đã thanh toán) -> không cho đăng ký lại
            if (oldStatus == JoinStatus.DaDuyet && oldRegister.getIsPaid() != null && oldRegister.getIsPaid()) {
                throw new AppException(ErrorCode.ALREADY_MEMBER);
            }
            
            // Nếu status = TuChoi, DaRoiCLB, hoặc HetHan -> CHO PHÉP tái gia nhập
            // Xóa registration cũ để tạo mới (giữ lại lịch sử trong database)
            if (oldStatus == JoinStatus.TuChoi || oldStatus == JoinStatus.DaRoiCLB || oldStatus == JoinStatus.HetHan) {
                registerRepository.delete(oldRegister);
                log.info("User {} re-applying to club {} after previous status: {}", 
                        currentUser.getEmail(), clubId, oldStatus);
            }
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
     * Xem danh sách các đơn đăng ký tham gia CLB của user hiện tại
     * 
     * Phương thức này cho phép Student xem tất cả các đơn đăng ký tham gia CLB của mình,
     * bao gồm các trạng thái: ChoDuyet, DaDuyet, TuChoi, DaRoiCLB, HetHan.
     * 
     * Tính năng đặc biệt:
     * - Tự động kiểm tra và cập nhật status membership hết hạn (lazy evaluation)
     * - Hiển thị đầy đủ thông tin về CLB, gói membership, trạng thái, ngày tham gia...
     * 
     * @return List<RegisterResponse> - Danh sách đơn đăng ký đã được map sang DTO
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user (từ getCurrentUser)
     * 
     * Lưu ý:
     * - Chỉ trả về đơn của chính user hiện tại (xác định từ JWT token)
     * - Tự động update status = HetHan nếu membership đã hết hạn (trước khi trả về)
     */
    public List<RegisterResponse> getMyRegistrations() {
        Users currentUser = getCurrentUser();
        List<Registers> registrations = registerRepository.findByUser(currentUser);
        
        // Lazy evaluation: Check và update expiry khi user fetch data
        checkAndUpdateExpiryBatch(registrations);
        
        return registerMapper.toRegisterResponseList(registrations);
    }

    /**
     * Xem chi tiết một đơn đăng ký cụ thể
     * 
     * Phương thức này cho phép:
     * 1. Student xem chi tiết đơn đăng ký của chính mình
     * 2. Leader (ChuTich, PhoChuTich) của CLB xem đơn đăng ký trong CLB của họ
     * 
     * Authorization logic:
     * - Nếu user là owner của đơn (user tạo đơn) → Cho phép
     * - Nếu user là Leader của CLB chứa đơn này → Cho phép
     * - Nếu không thỏa mãn cả hai điều kiện trên → Throw UNAUTHORIZED
     * 
     * Tính năng đặc biệt:
     * - Tự động kiểm tra và cập nhật status membership hết hạn (lazy evaluation)
     * 
     * @param subscriptionId - ID của đơn đăng ký cần xem chi tiết
     * @return RegisterResponse - Thông tin chi tiết đơn đăng ký đã được map sang DTO
     * @throws AppException với ErrorCode.REGISTER_NOT_FOUND nếu không tìm thấy đơn
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user (từ getCurrentUser)
     * @throws AppException với ErrorCode.UNAUTHORIZED nếu user không có quyền xem đơn này
     */
    public RegisterResponse getRegistrationById(Integer subscriptionId) {
        Users currentUser = getCurrentUser();
        
        Registers register = registerRepository.findById(subscriptionId)
                .orElseThrow(() -> new AppException(ErrorCode.REGISTER_NOT_FOUND));
        
        // Lazy evaluation: Check và update expiry khi user fetch data
        checkAndUpdateExpiry(register);
        
        // Kiểm tra quyền truy cập:
        // 1. User có phải owner của đăng ký này không?
        boolean isOwner = register.getUser().getUserId().equals(currentUser.getUserId());
        
        // 2. User có phải Leader của CLB này không?
        Integer clubId = register.getMembershipPackage().getClub().getClubId();
        List<ClubRoleType> leaderRoles = List.of(ClubRoleType.ChuTich, ClubRoleType.PhoChuTich);
        boolean isLeader = registerRepository.existsByUserAndMembershipPackage_Club_ClubIdAndClubRoleInAndStatusAndIsPaid(
                currentUser, clubId, leaderRoles, JoinStatus.DaDuyet, true);
        
        // Nếu không phải owner và không phải leader -> throw UNAUTHORIZED
        if (!isOwner && !isLeader) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        
        return registerMapper.toRegisterResponse(register);
    }

    /**
     * Hủy đơn đăng ký (chỉ khi trạng thái còn ChoDuyet)
     * 
     * Phương thức này cho phép Student hủy đơn đăng ký của mình khi:
     * - Đơn còn ở trạng thái ChoDuyet (chờ duyệt)
     * - User là owner của đơn (chỉ có thể hủy đơn của chính mình)
     * 
     * Business rules:
     * - Chỉ được hủy khi status = ChoDuyet (chưa được duyệt)
     * - Không thể hủy khi đã được duyệt (status = DaDuyet) → Phải dùng leaveClub() nếu muốn rời CLB
     * - Sau khi hủy, đơn sẽ bị xóa khỏi database (hard delete)
     * 
     * @param subscriptionId - ID của đơn đăng ký cần hủy
     * @throws AppException với ErrorCode.REGISTER_NOT_FOUND nếu không tìm thấy đơn
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user (từ getCurrentUser)
     * @throws AppException với ErrorCode.UNAUTHORIZED nếu user không phải owner của đơn
     * @throws AppException với ErrorCode.INVALID_APPLICATION_STATUS nếu status không phải ChoDuyet
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
     * 
     * Phương thức này cho phép Student rời khỏi CLB mà họ đang là thành viên.
     * 
     * Business rules:
     * - Chỉ được rời khi đang là thành viên active (status = DaDuyet, isPaid = true)
     * - ChuTich (Chủ tịch) không thể rời CLB → Phải chuyển quyền trước (throw PRESIDENT_CANNOT_LEAVE)
     * - Sau khi rời, status sẽ được set thành DaRoiCLB (đã rời CLB)
     * - Đơn đăng ký vẫn được giữ lại trong database (không xóa) để lưu lịch sử
     * 
     * @param clubId - ID của CLB muốn rời
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user (từ getCurrentUser)
     * @throws AppException với ErrorCode.PRESIDENT_CANNOT_LEAVE nếu user là ChuTich
     * @throws AppException với ErrorCode.NOT_CLUB_MEMBER nếu không tìm thấy đơn đăng ký trong CLB
     * @throws AppException với ErrorCode.NOT_ACTIVE_MEMBER nếu không phải thành viên active
     * 
     * Lưu ý: Khác với cancelRegistration() (hủy đơn chờ duyệt), leaveClub() dùng cho thành viên đã active
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

    /**
     * Gia hạn membership (renew subscription)
     * 
     * Phương thức này cho phép Student gia hạn membership khi đã hết hạn.
     * Có thể gia hạn với gói hiện tại hoặc đổi sang gói khác (nâng cấp/hạ cấp).
     * 
     * Quy trình gia hạn:
     * 1. Student chọn đơn đăng ký đã hết hạn (status = HetHan)
     * 2. Chọn gói membership (có thể giữ gói cũ hoặc đổi gói mới)
     * 3. Gọi renewMembership() → Update đơn với status = ChoDuyet (chờ thanh toán)
     * 4. Student thanh toán → isPaid = true, startDate/endDate được update
     * 5. Membership được gia hạn thành công
     * 
     * Business rules:
     * - Chỉ được gia hạn khi status = HetHan (đã hết hạn)
     * - User phải là owner của đơn đăng ký (chỉ có thể gia hạn đơn của chính mình)
     * - Nếu đổi gói, gói mới phải cùng CLB với gói cũ
     * - Nếu đổi gói, gói mới phải active (isActive = true)
     * - Sau khi gia hạn, đơn sẽ có status = ChoDuyet, cần thanh toán lại
     * 
     * @param subscriptionId - ID của đơn đăng ký cần gia hạn
     * @param request - DTO chứa packageId (optional):
     *                - null = gia hạn với gói hiện tại
     *                - có giá trị = đổi sang gói mới (nâng cấp/hạ cấp)
     * @return RegisterResponse - Thông tin đơn sau khi được gia hạn (với status = ChoDuyet, chờ thanh toán)
     * @throws AppException với ErrorCode.REGISTER_NOT_FOUND nếu không tìm thấy đơn
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user (từ getCurrentUser)
     * @throws AppException với ErrorCode.UNAUTHORIZED nếu user không phải owner của đơn
     * @throws AppException với ErrorCode.CANNOT_RENEW_SUBSCRIPTION nếu status không phải HetHan
     * @throws AppException với ErrorCode.PACKAGE_NOT_FOUND nếu không tìm thấy gói mới (khi đổi gói)
     * @throws AppException với ErrorCode.PACKAGE_NOT_ACTIVE nếu gói mới không active
     * @throws AppException với ErrorCode.INVALID_REQUEST nếu gói mới không cùng CLB
     * 
     * Lưu ý:
     * - Đơn đăng ký không bị xóa, chỉ được update (giữ lại lịch sử)
     * - Các field PayOS sẽ được reset để tạo payment link mới
     * - startDate và endDate cũ được giữ nguyên, sẽ được update sau khi thanh toán thành công
     */
    public RegisterResponse renewMembership(Integer subscriptionId, RenewMembershipRequest request) {
        Users currentUser = getCurrentUser();
        
        // Tìm subscription cần gia hạn
        Registers register = registerRepository.findById(subscriptionId)
                .orElseThrow(() -> new AppException(ErrorCode.REGISTER_NOT_FOUND));
        
        // Kiểm tra subscription có phải của user hiện tại không
        if (!register.getUser().getUserId().equals(currentUser.getUserId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        
        // Chỉ cho phép gia hạn khi status = HetHan
        if (register.getStatus() != JoinStatus.HetHan) {
            throw new AppException(ErrorCode.CANNOT_RENEW_SUBSCRIPTION);
        }
        
        // Xác định gói membership: giữ gói cũ hoặc đổi gói mới
        Memberships newPackage;
        if (request != null && request.getPackageId() != null) {
            // Đổi sang gói mới (nâng cấp/hạ cấp)
            newPackage = membershipRepository.findById(request.getPackageId())
                    .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_NOT_FOUND));
            
            // Kiểm tra gói mới có cùng CLB không
            if (!newPackage.getClub().getClubId().equals(register.getMembershipPackage().getClub().getClubId())) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }
            
            // Kiểm tra gói mới có active không
            if (!newPackage.getIsActive()) {
                throw new AppException(ErrorCode.PACKAGE_NOT_ACTIVE);
            }
            
            log.info("User {} upgrading/downgrading from package {} to package {}", 
                    currentUser.getEmail(), 
                    register.getMembershipPackage().getPackageName(), 
                    newPackage.getPackageName());
        } else {
            // Giữ gói hiện tại
            newPackage = register.getMembershipPackage();
            log.info("User {} renewing same package {}", 
                    currentUser.getEmail(), 
                    newPackage.getPackageName());
        }
        
        // Cập nhật subscription để gia hạn (không xóa, giữ lại lịch sử)
        register.setMembershipPackage(newPackage);
        register.setStatus(JoinStatus.ChoDuyet); // Chờ thanh toán
        register.setIsPaid(false);
        register.setPaymentDate(null);
        register.setPaymentMethod(null);
        // Reset PayOS fields để tạo payment link mới
        register.setPayosOrderCode(null);
        register.setPayosPaymentLinkId(null);
        register.setPayosReference(null);
        // Giữ nguyên startDate, endDate cũ (sẽ update sau khi thanh toán thành công)
        
        registerRepository.save(register);
        
        log.info("User {} successfully renewed subscription {} with package {}", 
                currentUser.getEmail(), subscriptionId, newPackage.getPackageName());
        
        return registerMapper.toRegisterResponse(register);
    }
}

