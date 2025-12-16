package com.swp391.clubmanagement.service;

import com.swp391.clubmanagement.dto.request.ClubUpdateRequest;
import com.swp391.clubmanagement.dto.response.ClubMemberResponse;
import com.swp391.clubmanagement.dto.response.ClubResponse;
import com.swp391.clubmanagement.dto.response.ClubStatsResponse;
import com.swp391.clubmanagement.dto.response.JoinedClubResponse;
import com.swp391.clubmanagement.entity.Clubs;
import com.swp391.clubmanagement.entity.Registers;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.enums.ClubCategory;
import com.swp391.clubmanagement.enums.ClubRoleType;
import com.swp391.clubmanagement.enums.JoinStatus;
import com.swp391.clubmanagement.exception.AppException;
import com.swp391.clubmanagement.exception.ErrorCode;
import com.swp391.clubmanagement.mapper.ClubMapper;
import com.swp391.clubmanagement.repository.ClubRepository;
import com.swp391.clubmanagement.repository.RegisterRepository;
import com.swp391.clubmanagement.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ClubService {
    
    ClubRepository clubRepository;
    RegisterRepository registerRepository;
    UserRepository userRepository;
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
        // QUAN TRỌNG: Tính doanh thu từ TẤT CẢ member đã thanh toán, bất kể status hiện tại
        // (kể cả DaRoiCLB, HetHan) - không trừ doanh thu khi member rời club hoặc bị kick
        Users founder = club.getFounder();
        YearMonth currentMonth = YearMonth.now();
        BigDecimal totalRevenue = allRegisters.stream()
                .filter(r -> r.getIsPaid() != null && r.getIsPaid() && r.getPaymentDate() != null)
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
                // Không filter theo status - tính tất cả member đã thanh toán, kể cả đã rời (DaRoiCLB)
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
}
