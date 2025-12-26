// Package định nghĩa service layer - xử lý business logic cho quản lý CLB
package com.swp391.clubmanagement.service;

// ========== DTO ==========
import com.swp391.clubmanagement.dto.request.ClubUpdateRequest; // Request cập nhật CLB
import com.swp391.clubmanagement.dto.response.ClubMemberResponse; // Response danh sách thành viên
import com.swp391.clubmanagement.dto.response.ClubResponse; // Response thông tin CLB
import com.swp391.clubmanagement.dto.response.ClubStatsResponse; // Response thống kê CLB
import com.swp391.clubmanagement.dto.response.JoinedClubResponse; // Response CLB đã tham gia

// ========== Entity ==========
import com.swp391.clubmanagement.entity.ClubApplications; // Entity đơn yêu cầu thành lập CLB
import com.swp391.clubmanagement.entity.Clubs; // Entity CLB
import com.swp391.clubmanagement.entity.Memberships; // Entity gói membership
import com.swp391.clubmanagement.entity.Registers; // Entity đăng ký tham gia CLB
import com.swp391.clubmanagement.entity.Users; // Entity người dùng

// ========== Enum ==========
import com.swp391.clubmanagement.enums.ClubCategory; // Danh mục CLB
import com.swp391.clubmanagement.enums.ClubRoleType; // Vai trò trong CLB
import com.swp391.clubmanagement.enums.JoinStatus; // Trạng thái tham gia
import com.swp391.clubmanagement.enums.RoleType; // Vai trò hệ thống

// ========== Exception ==========
import com.swp391.clubmanagement.exception.AppException; // Custom exception
import com.swp391.clubmanagement.exception.ErrorCode; // Mã lỗi hệ thống

// ========== Mapper ==========
import com.swp391.clubmanagement.mapper.ClubMapper; // Chuyển đổi Entity <-> DTO

// ========== Repository ==========
import com.swp391.clubmanagement.repository.ClubApplicationRepository; // Repository cho bảng ClubApplications
import com.swp391.clubmanagement.repository.ClubRepository; // Repository cho bảng Clubs
import com.swp391.clubmanagement.repository.MembershipRepository; // Repository cho bảng Memberships
import com.swp391.clubmanagement.repository.RegisterRepository; // Repository cho bảng Registers
import com.swp391.clubmanagement.repository.RoleRepository; // Repository cho bảng Roles
import com.swp391.clubmanagement.repository.UserRepository; // Repository cho bảng Users

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
import java.math.BigDecimal; // Số tiền (doanh thu)
import java.time.LocalDateTime; // Ngày giờ
import java.time.YearMonth; // Năm-tháng (để tính doanh thu theo tháng)
import java.time.format.DateTimeFormatter; // Format ngày giờ
import java.util.Arrays; // Mảng
import java.util.List; // Danh sách
import java.util.stream.Collectors; // Collect stream thành collection

/**
 * Service quản lý CLB
 * 
 * Chức năng chính:
 * - Xem danh sách CLB (có thể search và filter)
 * - Xem chi tiết CLB
 * - Cập nhật thông tin CLB (Leader only)
 * - Xem danh sách thành viên CLB
 * - Thống kê CLB (Leader only): số thành viên, doanh thu, etc.
 * - Xem CLB đã tham gia (Student)
 * - Xóa CLB (Admin only)
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
public class ClubService {
    
    /** Repository thao tác với bảng clubs */
    ClubRepository clubRepository;
    
    /** Repository thao tác với bảng club_applications */
    ClubApplicationRepository clubApplicationRepository;
    
    /** Repository thao tác với bảng memberships */
    MembershipRepository membershipRepository;
    
    /** Repository thao tác với bảng registers */
    RegisterRepository registerRepository;
    
    /** Repository thao tác với bảng users */
    UserRepository userRepository;
    
    /** Repository thao tác với bảng roles */
    RoleRepository roleRepository;
    
    /** Mapper chuyển đổi Entity (Clubs) <-> DTO (ClubResponse) */
    ClubMapper clubMapper;
    
    /**
     * Helper: Kiểm tra và tự động cập nhật status nếu membership đã hết hạn
     * (Lazy Evaluation - chỉ check khi cần)
     * 
     * @param register Register cần kiểm tra
     * @return true nếu đã update status, false nếu chưa hết hạn
     */
    private boolean checkAndUpdateExpiry(Registers register) {
        LocalDateTime now = LocalDateTime.now();
        
        // Log để debug
        log.debug("Checking expiry for subscription {}: status={}, isPaid={}, endDate={}, now={}", 
                register.getSubscriptionId(),
                register.getStatus(),
                register.getIsPaid(),
                register.getEndDate(),
                now);
        
        // Chỉ check nếu: status = DaDuyet + đã thanh toán + có endDate
        if (register.getStatus() == JoinStatus.DaDuyet 
            && register.getIsPaid() != null && register.getIsPaid()
            && register.getEndDate() != null
            && register.getEndDate().isBefore(now)) {
            
            // Update status thành HetHan
            register.setStatus(JoinStatus.HetHan);
            registerRepository.save(register);
            
            log.info("✅ Auto-updated subscription {} to HetHan. User: {}, Club: {}, EndDate: {} < Now: {}", 
                    register.getSubscriptionId(),
                    register.getUser().getEmail(),
                    register.getMembershipPackage().getClub().getClubName(),
                    register.getEndDate(),
                    now);
            
            return true;
        }
        
        // Log lý do không update
        if (register.getStatus() != JoinStatus.DaDuyet) {
            log.debug("❌ Skip: Status is not DaDuyet (current: {})", register.getStatus());
        } else if (register.getIsPaid() == null || !register.getIsPaid()) {
            log.debug("❌ Skip: Not paid yet");
        } else if (register.getEndDate() == null) {
            log.debug("❌ Skip: No endDate");
        } else if (!register.getEndDate().isBefore(now)) {
            log.debug("❌ Skip: Not expired yet (endDate: {} >= now: {})", register.getEndDate(), now);
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
     * Lấy danh sách tất cả CLB đang hoạt động (Public)
     * Có thể search theo tên và filter theo category
     */
    public List<ClubResponse> getAllClubs(String name, ClubCategory category) {
        List<Clubs> clubs;
        
        if (name != null || category != null) {
            clubs = clubRepository.searchByNameAndCategory(name, category);
        } else {
            clubs = clubRepository.findByIsActiveTrue();
        }
        
        return clubs.stream()
                .map(club -> {
                    ClubResponse response = clubMapper.toResponse(club);
                    // Đếm tổng số thành viên chính thức (đã duyệt và đã đóng phí)
                    long totalMembers = registerRepository.countByMembershipPackage_Club_ClubIdAndStatusAndIsPaid(
                            club.getClubId(), JoinStatus.DaDuyet, true);
                    response.setTotalMembers(totalMembers);
                    return response;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Xem chi tiết thông tin 1 CLB (Public)
     */
    public ClubResponse getClubById(Integer clubId) {
        Clubs club = clubRepository.findById(clubId)
                .orElseThrow(() -> new AppException(ErrorCode.CLUB_NOT_FOUND));
        
        ClubResponse response = clubMapper.toResponse(club);
        
        // Đếm tổng số thành viên chính thức (đã duyệt và đã đóng phí)
        long totalMembers = registerRepository.countByMembershipPackage_Club_ClubIdAndStatusAndIsPaid(
                clubId, JoinStatus.DaDuyet, true);
        response.setTotalMembers(totalMembers);
        
        return response;
    }
    
    /**
     * Cập nhật thông tin CLB - Logo, mô tả, địa điểm sinh hoạt (Leader only)
     */
    @Transactional
    public ClubResponse updateClub(Integer clubId, ClubUpdateRequest request) {
        // Lấy thông tin user hiện tại
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Users currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        // Tìm CLB
        Clubs club = clubRepository.findById(clubId)
                .orElseThrow(() -> new AppException(ErrorCode.CLUB_NOT_FOUND));
        
        // Kiểm tra quyền: chỉ founder mới được cập nhật
        // (Trong thực tế, có thể check thêm ClubRoleType.Leader trong bảng Registers)
        if (!club.getFounder().getUserId().equals(currentUser.getUserId())) {
            throw new AppException(ErrorCode.NOT_CLUB_LEADER);
        }
        
        // Cập nhật thông tin
        if (request.getLogo() != null) {
            club.setLogo(request.getLogo());
        }
        
        if (request.getDescription() != null) {
            club.setDescription(request.getDescription());
        }
        
        if (request.getLocation() != null) {
            club.setLocation(request.getLocation());
        }
        if (request.getEmail() != null) {
            // Optional: Kiểm tra email có bị trùng với CLB khác không
            if (clubRepository.existsByEmailAndClubIdNot(request.getEmail(), clubId)) {
                throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
            club.setEmail(request.getEmail());
        }
        
        club = clubRepository.save(club);
        log.info("Club {} updated by leader {}", clubId, currentUser.getEmail());
        
        return clubMapper.toResponse(club);
    }
    
    /**
     * Xem danh sách thành viên của CLB (Public)
     */
    public List<ClubMemberResponse> getClubMembers(Integer clubId) {
        // Kiểm tra CLB có tồn tại không
        if (!clubRepository.existsById(clubId)) {
            throw new AppException(ErrorCode.CLUB_NOT_FOUND);
        }
        
        // Lấy danh sách thành viên đã được duyệt và đã đóng phí
        List<Registers> registers = registerRepository.findByMembershipPackage_Club_ClubIdAndStatus(clubId, JoinStatus.DaDuyet);
        
        log.debug("Found {} registers for club {} with status DaDuyet", registers.size(), clubId);
        
        return registers.stream()
                .filter(r -> {
                    boolean isPaid = r.getIsPaid();
                    log.debug("Register {}: userId={}, clubRole={}, isPaid={}", 
                            r.getSubscriptionId(), r.getUser().getUserId(), r.getClubRole(), isPaid);
                    return isPaid;
                }) // Chỉ lấy những người đã đóng phí
                .map(clubMapper::toMemberResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Thống kê nội bộ CLB (Leader only)
     * - Số lượng thành viên, tổng doanh thu từ phí thành viên
     * - Danh sách chưa đóng phí
     */
    public ClubStatsResponse getClubStats(Integer clubId) {
        // Lấy user hiện tại
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Users currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        // Kiểm tra CLB có tồn tại
        Clubs club = clubRepository.findById(clubId)
                .orElseThrow(() -> new AppException(ErrorCode.CLUB_NOT_FOUND));
        
        // Kiểm tra quyền: Phải là Leader hoặc Founder
        boolean isLeader = registerRepository.existsByUserAndMembershipPackage_Club_ClubIdAndClubRoleInAndStatusAndIsPaid(
                currentUser,
                clubId,
                Arrays.asList(ClubRoleType.ChuTich, ClubRoleType.PhoChuTich),
                JoinStatus.DaDuyet,
                true
        );
        
        if (!isLeader && !club.getFounder().getUserId().equals(currentUser.getUserId())) {
            throw new AppException(ErrorCode.NOT_CLUB_LEADER);
        }
        
        // Lấy tất cả đăng ký của CLB
        List<Registers> allRegisters = registerRepository.findByMembershipPackage_Club_ClubId(clubId);
        
        // Thống kê thành viên
        long totalMembers = allRegisters.stream()
                .filter(r -> r.getStatus() == JoinStatus.DaDuyet && r.getIsPaid())
                .count();
        
        long pendingCount = allRegisters.stream()
                .filter(r -> r.getStatus() == JoinStatus.ChoDuyet)
                .count();
        
        long rejectedCount = allRegisters.stream()
                .filter(r -> r.getStatus() == JoinStatus.TuChoi)
                .count();
        
        // Thống kê theo vai trò
        List<Registers> activeMembers = allRegisters.stream()
                .filter(r -> r.getStatus() == JoinStatus.DaDuyet && r.getIsPaid())
                .collect(Collectors.toList());
        
        long chuTichCount = activeMembers.stream()
                .filter(r -> r.getClubRole() == ClubRoleType.ChuTich)
                .count();
        
        long phoChuTichCount = activeMembers.stream()
                .filter(r -> r.getClubRole() == ClubRoleType.PhoChuTich)
                .count();
        
        long thuKyCount = activeMembers.stream()
                .filter(r -> r.getClubRole() == ClubRoleType.ThuKy)
                .count();
        
        long thanhVienCount = activeMembers.stream()
                .filter(r -> r.getClubRole() == ClubRoleType.ThanhVien)
                .count();
        
        // Thống kê tài chính - Tính doanh thu theo tháng (chỉ tính những người đã trả tiền, trừ founder)
        Users founder = club.getFounder();
        YearMonth currentMonth = YearMonth.now();
        BigDecimal totalRevenue = allRegisters.stream()
                .filter(r -> r.getIsPaid() && r.getPaymentDate() != null)
                .filter(r -> {
                    // Loại trừ tiền của founder
                    if (founder != null && r.getUser().getUserId().equals(founder.getUserId())) {
                        return false;
                    }
                    // Chỉ tính thanh toán trong tháng hiện tại
                    LocalDateTime paymentDate = r.getPaymentDate();
                    YearMonth paymentMonth = YearMonth.from(paymentDate);
                    return paymentMonth.equals(currentMonth);
                })
                .map(r -> r.getMembershipPackage().getPrice())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        long paidCount = allRegisters.stream()
                .filter(r -> r.getStatus() == JoinStatus.DaDuyet && r.getIsPaid())
                .count();
        
        long unpaidCount = allRegisters.stream()
                .filter(r -> r.getStatus() == JoinStatus.DaDuyet && !r.getIsPaid())
                .count();
        
        // Danh sách chưa đóng phí
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        List<ClubStatsResponse.UnpaidMemberInfo> unpaidMembers = allRegisters.stream()
                .filter(r -> r.getStatus() == JoinStatus.DaDuyet && !r.getIsPaid())
                .map(r -> ClubStatsResponse.UnpaidMemberInfo.builder()
                        .subscriptionId(r.getSubscriptionId())
                        .studentCode(r.getUser().getStudentCode())
                        .fullName(r.getUser().getFullName())
                        .packageName(r.getMembershipPackage().getPackageName())
                        .packagePrice(r.getMembershipPackage().getPrice())
                        .joinDate(r.getJoinDate() != null ? r.getJoinDate().format(formatter) : null)
                        .build())
                .collect(Collectors.toList());
        
        return ClubStatsResponse.builder()
                .clubId(clubId)
                .clubName(club.getClubName())
                .totalMembers(totalMembers)
                .pendingRegistrations(pendingCount)
                .rejectedRegistrations(rejectedCount)
                .chuTichCount(chuTichCount)
                .phoChuTichCount(phoChuTichCount)
                .thuKyCount(thuKyCount)
                .thanhVienCount(thanhVienCount)
                .totalRevenue(totalRevenue)
                .paidCount(paidCount)
                .unpaidCount(unpaidCount)
                .unpaidMembers(unpaidMembers)
                .build();
    }
    
    /**
     * Lấy danh sách CLB mà student đã tham gia (bao gồm đang hoạt động và hết hạn)
     * @param userId ID của user (student)
     * @return Danh sách CLB mà user đã tham gia (DaDuyet hoặc HetHan)
     */
    public List<JoinedClubResponse> getJoinedClubsByUser(String userId) {
        // Lấy user
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        // Lấy tất cả đăng ký của user
        List<Registers> allRegisters = registerRepository.findByUser(user);
        
        // Lazy evaluation: Check và update expiry trước
        checkAndUpdateExpiryBatch(allRegisters);
        
        // ✅ QUAN TRỌNG: Re-fetch từ DB sau khi update để có status mới
        allRegisters = registerRepository.findByUser(user);
        
        // Filter: Chỉ lấy DaDuyet (đã thanh toán) hoặc HetHan
        List<Registers> registers = allRegisters.stream()
                .filter(r -> {
                    // DaDuyet + đã thanh toán HOẶC HetHan
                    if (r.getStatus() == JoinStatus.DaDuyet && r.getIsPaid()) {
                        return true;
                    }
                    return r.getStatus() == JoinStatus.HetHan;
                })
                .collect(Collectors.toList());
        
        // Log để debug
        log.debug("Found {} registers after filtering (DaDuyet paid or HetHan)", registers.size());
        
        // Chuyển đổi sang JoinedClubResponse
        return registers.stream()
                .map(register -> {
                    Clubs club = register.getMembershipPackage().getClub();
                    ClubResponse clubResponse = clubMapper.toResponse(club);
                    
                    // Tính isExpired: endDate < now
                    boolean isExpired = register.getEndDate() != null 
                            && register.getEndDate().isBefore(LocalDateTime.now());
                    
                    // Tính canRenew: status = HetHan
                    boolean canRenew = register.getStatus() == JoinStatus.HetHan;
                    
                    // Log để debug
                    log.debug("Register {}: status={}, endDate={}, isExpired={}, canRenew={}", 
                            register.getSubscriptionId(), 
                            register.getStatus(), 
                            register.getEndDate(),
                            isExpired, 
                            canRenew);
                    
                    return JoinedClubResponse.builder()
                            .clubId(clubResponse.getClubId())
                            .clubName(clubResponse.getClubName())
                            .category(clubResponse.getCategory())
                            .logo(clubResponse.getLogo())
                            .location(clubResponse.getLocation())
                            .description(clubResponse.getDescription())
                            .email(clubResponse.getEmail())
                            .isActive(clubResponse.getIsActive())
                            .establishedDate(clubResponse.getEstablishedDate())
                            .founderId(clubResponse.getFounderId())
                            .founderName(clubResponse.getFounderName())
                            .founderStudentCode(clubResponse.getFounderStudentCode())
                            // Thêm các field mới cho gia hạn
                            .subscriptionId(register.getSubscriptionId())
                            .packageId(register.getMembershipPackage().getPackageId())
                            .packageName(register.getMembershipPackage().getPackageName())
                            .clubRole(register.getClubRole())
                            .joinedAt(register.getJoinDate())
                            .endDate(register.getEndDate())
                            .canRenew(canRenew)
                            .isExpired(isExpired)
                            .build();
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Xóa CLB (Admin only)
     * - Tìm tất cả members của club
     * - Chuyển Chủ tịch về role SinhVien
     * - Xóa tất cả registrations của club
     * - Xóa tất cả membership packages của club
     * - Xóa tất cả club applications liên quan
     * - Xóa club
     * 
     * Thứ tự xóa quan trọng để tránh foreign key constraint:
     * 1. Registrations (FK -> Memberships)
     * 2. Memberships (FK -> Clubs)
     * 3. ClubApplications (FK -> Clubs)
     * 4. Clubs
     */
    @Transactional
    public void deleteClub(Integer clubId) {
        // Tìm CLB
        Clubs club = clubRepository.findById(clubId)
                .orElseThrow(() -> new AppException(ErrorCode.CLUB_NOT_FOUND));
        
        // Lấy tất cả registrations của CLB
        List<Registers> allRegistrations = registerRepository.findByMembershipPackage_Club_ClubId(clubId);
        
        log.info("🗑️ Deleting club {} ({}) with {} registrations", 
                clubId, club.getClubName(), allRegistrations.size());
        
        // Bước 1: Tìm Chủ tịch của CLB (nếu có) và chuyển về SinhVien
        List<Registers> presidentRegistrations = allRegistrations.stream()
                .filter(r -> r.getClubRole() == ClubRoleType.ChuTich)
                .filter(r -> r.getStatus() == JoinStatus.DaDuyet)
                .filter(r -> r.getIsPaid() != null && r.getIsPaid())
                .collect(Collectors.toList());
        
        // Chuyển tất cả Chủ tịch về role SinhVien
        for (Registers presidentReg : presidentRegistrations) {
            Users president = presidentReg.getUser();
            
            // Kiểm tra role hiện tại của president
            if (president.getRole().getRoleName() == RoleType.ChuTich) {
                // Chuyển về SinhVien
                var sinhVienRole = roleRepository.findByRoleName(RoleType.SinhVien)
                        .orElseThrow(() -> {
                            log.error("SinhVien role not found in database. Please check ApplicationInitConfig.");
                            return new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
                        });
                
                president.setRole(sinhVienRole);
                userRepository.save(president);
                
                log.info("✅ Changed president {} role from ChuTich to SinhVien (club {} is being deleted)", 
                        president.getEmail(), clubId);
            }
        }
        
        // Bước 2: Xóa tất cả registrations của club (FK -> Memberships)
        registerRepository.deleteAll(allRegistrations);
        log.info("✅ Deleted {} registrations for club {}", allRegistrations.size(), clubId);
        
        // Bước 3: Xóa tất cả membership packages của club (FK -> Clubs)
        List<Memberships> allMemberships = membershipRepository.findByClub_ClubId(clubId);
        membershipRepository.deleteAll(allMemberships);
        log.info("✅ Deleted {} membership packages for club {}", allMemberships.size(), clubId);
        
        // Bước 4: Xóa tất cả club applications liên quan (FK -> Clubs)
        List<ClubApplications> allApplications = clubApplicationRepository.findByClub(club);
        clubApplicationRepository.deleteAll(allApplications);
        log.info("✅ Deleted {} club applications for club {}", allApplications.size(), clubId);
        
        // Bước 5: Xóa club
        clubRepository.delete(club);
        log.info("✅ Successfully deleted club {} ({})", clubId, club.getClubName());
    }
}
