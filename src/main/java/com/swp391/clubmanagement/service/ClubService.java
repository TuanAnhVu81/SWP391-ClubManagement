package com.swp391.clubmanagement.service;

import com.swp391.clubmanagement.dto.request.ClubUpdateRequest;
import com.swp391.clubmanagement.dto.response.ClubMemberResponse;
import com.swp391.clubmanagement.dto.response.ClubResponse;
import com.swp391.clubmanagement.dto.response.ClubStatsResponse;
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
}
