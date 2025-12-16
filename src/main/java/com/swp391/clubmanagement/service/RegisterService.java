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
     * Helper: Kiểm tra và tự động cập nhật status nếu membership đã hết hạn
     * (Lazy Evaluation - chỉ check khi cần)
     * 
     * @param register Register cần kiểm tra
     * @return true nếu đã update status, false nếu chưa hết hạn
     */
    private boolean checkAndUpdateExpiry(Registers register) {
        // Chỉ check nếu: status = DaDuyet + đã thanh toán + có endDate
        if (register.getStatus() == JoinStatus.DaDuyet 
            && register.getIsPaid() != null && register.getIsPaid()
            && register.getEndDate() != null
            && register.getEndDate().isBefore(LocalDateTime.now())) {
            
            // Update status thành HetHan
            register.setStatus(JoinStatus.HetHan);
            registerRepository.save(register);
            
            log.info("Auto-updated subscription {} to HetHan. User: {}, Club: {}, EndDate: {}", 
                    register.getSubscriptionId(),
                    register.getUser().getEmail(),
                    register.getMembershipPackage().getClub().getClubName(),
                    register.getEndDate());
            
            return true;
        }
        return false;
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
     * Đăng ký tham gia CLB (mua gói package)
     * Trạng thái mặc định: ChoDuyet
     * 
     * Logic tái gia nhập:
     * - Nếu chưa có registration -> Tạo mới
     * - Nếu status = ChoDuyet -> Không cho đăng ký lại (đang chờ duyệt)
     * - Nếu status = DaDuyet + isPaid -> Không cho đăng ký lại (đã là thành viên)
     * - Nếu status = TuChoi hoặc DaRoiCLB -> CHO PHÉP đăng ký lại (tạo mới)
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
            // QUAN TRỌNG: Không xóa registration cũ để giữ lại lịch sử thanh toán (doanh thu)
            // Thay vào đó, cập nhật lại registration cũ để tái sử dụng
            if (oldStatus == JoinStatus.TuChoi || oldStatus == JoinStatus.DaRoiCLB || oldStatus == JoinStatus.HetHan) {
                // Cập nhật lại registration cũ thay vì xóa
                // Giữ lại thông tin thanh toán cũ (isPaid, paymentDate) để không mất doanh thu
                oldRegister.setStatus(JoinStatus.ChoDuyet);
                oldRegister.setJoinReason(request.getJoinReason());
                // Reset các trường thanh toán mới (sẽ được set lại khi thanh toán)
                // NHƯNG giữ lại thông tin thanh toán cũ nếu đã thanh toán trước đó
                // Chỉ reset nếu chưa thanh toán hoặc đã hết hạn
                if (oldStatus == JoinStatus.HetHan || (oldRegister.getIsPaid() != null && !oldRegister.getIsPaid())) {
                    oldRegister.setIsPaid(false);
                    oldRegister.setPaymentDate(null);
                    oldRegister.setPaymentMethod(null);
                }
                // Reset PayOS fields để tạo payment link mới
                oldRegister.setPayosOrderCode(null);
                oldRegister.setPayosPaymentLinkId(null);
                oldRegister.setPayosReference(null);
                // Reset startDate và endDate (sẽ được set lại khi thanh toán)
                oldRegister.setStartDate(null);
                oldRegister.setEndDate(null);
                
                registerRepository.save(oldRegister);
                log.info("User {} re-applying to club {} after previous status: {} (reusing registration {})", 
                        currentUser.getEmail(), clubId, oldStatus, oldRegister.getSubscriptionId());
                
                return registerMapper.toRegisterResponse(oldRegister);
            }
        }
        
        // Tạo đăng ký mới (chỉ khi chưa có registration cũ)
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
     * Tự động kiểm tra và cập nhật status nếu có membership hết hạn
     */
    public List<RegisterResponse> getMyRegistrations() {
        Users currentUser = getCurrentUser();
        List<Registers> registrations = registerRepository.findByUser(currentUser);
        
        // Lazy evaluation: Check và update expiry khi user fetch data
        checkAndUpdateExpiryBatch(registrations);
        
        return registerMapper.toRegisterResponseList(registrations);
    }

    /**
     * Xem chi tiết 1 đăng ký
     * Cho phép:
     * 1. User xem đăng ký của chính mình
     * 2. Leader (ChuTich, PhoChuTich) của CLB xem đăng ký trong CLB của họ
     * Tự động kiểm tra và cập nhật status nếu membership hết hạn
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

    /**
     * Gia hạn membership
     * @param subscriptionId ID của đăng ký cần gia hạn
     * @param request packageId (optional): null = gia hạn gói hiện tại, có giá trị = đổi gói
     * @return RegisterResponse với trạng thái ChoDuyet (chờ thanh toán)
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

