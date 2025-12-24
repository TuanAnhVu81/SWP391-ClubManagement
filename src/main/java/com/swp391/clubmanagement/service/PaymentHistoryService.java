// Package định nghĩa service layer - xử lý business logic cho lịch sử giao dịch và doanh thu
package com.swp391.clubmanagement.service;

// ========== DTO ==========
import com.swp391.clubmanagement.dto.response.PaymentHistoryResponse; // Response lịch sử giao dịch
import com.swp391.clubmanagement.dto.response.RevenueResponse; // Response doanh thu
import com.swp391.clubmanagement.dto.response.RevenueByMonthWithClubsResponse; // Response doanh thu theo tháng kèm CLB

// ========== Entity ==========
import com.swp391.clubmanagement.entity.Clubs; // Entity CLB
import com.swp391.clubmanagement.entity.PaymentHistory; // Entity lịch sử thanh toán
import com.swp391.clubmanagement.entity.Registers; // Entity đăng ký tham gia CLB
import com.swp391.clubmanagement.entity.Users; // Entity người dùng

// ========== Exception ==========
import com.swp391.clubmanagement.exception.AppException; // Custom exception
import com.swp391.clubmanagement.exception.ErrorCode; // Mã lỗi hệ thống

// ========== Repository ==========
import com.swp391.clubmanagement.repository.ClubRepository; // Repository cho bảng Clubs
import com.swp391.clubmanagement.repository.PaymentHistoryRepository; // Repository cho bảng PaymentHistory
import com.swp391.clubmanagement.repository.UserRepository; // Repository cho bảng Users

// ========== Lombok ==========
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor; // Tự động tạo constructor inject dependencies
import lombok.experimental.FieldDefaults; // Tự động thêm private final cho fields
import lombok.extern.slf4j.Slf4j; // Tự động tạo logger

// ========== Spring Framework ==========
import org.springframework.data.domain.Page; // Phân trang
import org.springframework.data.domain.Pageable; // Thông tin phân trang
import org.springframework.security.core.context.SecurityContextHolder; // Lấy user hiện tại từ JWT
import org.springframework.stereotype.Service; // Đánh dấu class là Spring Service Bean
import org.springframework.transaction.annotation.Transactional; // Quản lý transaction

// ========== Java Standard Library ==========
import java.math.BigDecimal; // Số tiền (doanh thu)
import java.time.LocalDateTime; // Ngày giờ
import java.util.*; // Collections
import java.util.stream.Collectors; // Collect stream thành collection

/**
 * Service xử lý lịch sử giao dịch và tính doanh thu
 * 
 * Chức năng chính:
 * - Tạo payment history record khi thanh toán thành công
 * - Xem lịch sử giao dịch của user hiện tại
 * - Xem lịch sử giao dịch của một CLB
 * - Tính tổng doanh thu của một CLB
 * - Tính doanh thu theo tháng (của CLB hoặc toàn hệ thống)
 * - Tính doanh thu theo tháng kèm danh sách doanh thu từng CLB
 * 
 * Business Rules:
 * - Payment history được tạo tự động khi Leader xác nhận thanh toán
 * - Doanh thu chỉ tính các giao dịch đã thanh toán thành công
 * - Có thể tính doanh thu theo CLB hoặc theo tháng
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
public class PaymentHistoryService {
    
    /** Repository thao tác với bảng payment_history */
    PaymentHistoryRepository paymentHistoryRepository;
    
    /** Repository thao tác với bảng users */
    UserRepository userRepository;
    
    /** Repository thao tác với bảng clubs */
    ClubRepository clubRepository;
    
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
     * Tạo payment history record khi thanh toán thành công
     */
    @Transactional
    public PaymentHistory createPaymentHistory(Registers register) {
        PaymentHistory paymentHistory = PaymentHistory.builder()
                .register(register)
                .user(register.getUser())
                .club(register.getMembershipPackage().getClub())
                .membershipPackage(register.getMembershipPackage())
                .amount(register.getMembershipPackage().getPrice())
                .paymentMethod(register.getPaymentMethod())
                .payosOrderCode(register.getPayosOrderCode())
                .payosReference(register.getPayosReference())
                .paymentDate(register.getPaymentDate())
                .build();
        
        paymentHistory = paymentHistoryRepository.save(paymentHistory);
        log.info("Created payment history record: paymentId={}, subscriptionId={}, amount={}", 
                paymentHistory.getPaymentId(), register.getSubscriptionId(), paymentHistory.getAmount());
        
        return paymentHistory;
    }
    
    /**
     * Xem lịch sử giao dịch của user hiện tại
     */
    public Page<PaymentHistoryResponse> getMyPaymentHistory(Pageable pageable) {
        Users currentUser = getCurrentUser();
        Page<PaymentHistory> paymentHistoryPage = paymentHistoryRepository.findByUser(currentUser, pageable);
        return paymentHistoryPage.map(this::toPaymentHistoryResponse);
    }
    
    /**
     * Xem lịch sử giao dịch của một CLB
     */
    public Page<PaymentHistoryResponse> getClubPaymentHistory(Integer clubId, Pageable pageable) {
        Clubs club = clubRepository.findById(clubId)
                .orElseThrow(() -> new AppException(ErrorCode.CLUB_NOT_FOUND));
        Page<PaymentHistory> paymentHistoryPage = paymentHistoryRepository.findByClub(club, pageable);
        return paymentHistoryPage.map(this::toPaymentHistoryResponse);
    }
    
    /**
     * Tính tổng doanh thu của một CLB
     */
    public RevenueResponse calculateRevenueByClub(Integer clubId) {
        Clubs club = clubRepository.findById(clubId)
                .orElseThrow(() -> new AppException(ErrorCode.CLUB_NOT_FOUND));
        
        BigDecimal totalRevenue = paymentHistoryRepository.calculateTotalRevenueByClub(clubId);
        Long transactionCount = paymentHistoryRepository.countByClub(club);
        
        return RevenueResponse.builder()
                .totalRevenue(totalRevenue)
                .transactionCount(transactionCount)
                .clubId(clubId)
                .clubName(club.getClubName())
                .build();
    }
    
    /**
     * Tính doanh thu của một CLB theo tháng
     */
    public List<RevenueResponse> calculateRevenueByClubByMonth(
            Integer clubId, LocalDateTime startDate, LocalDateTime endDate) {
        Clubs club = clubRepository.findById(clubId)
                .orElseThrow(() -> new AppException(ErrorCode.CLUB_NOT_FOUND));
        
        // Điều chỉnh endDate thành cuối ngày để tính đủ cả ngày hôm đó
        LocalDateTime adjustedEndDate = endDate.toLocalDate().atTime(23, 59, 59, 999999999);
        
        List<Object[]> results = paymentHistoryRepository.calculateRevenueByClubByMonth(
                clubId, startDate, adjustedEndDate);
        
        return results.stream().map(result -> {
            BigDecimal totalRevenue = (BigDecimal) result[2];
            Long transactionCount = ((Number) result[3]).longValue();
            
            return RevenueResponse.builder()
                    .clubId(clubId)
                    .clubName(club.getClubName())
                    .totalRevenue(totalRevenue)
                    .transactionCount(transactionCount)
                    .startDate(startDate)
                    .endDate(endDate)
                    .build();
        }).collect(Collectors.toList());
    }
    
    /**
     * Tính doanh thu theo tháng (giữ nguyên để tương thích với các API khác)
     */
    public List<RevenueResponse> calculateRevenueByMonth(
            LocalDateTime startDate, LocalDateTime endDate) {
        // Điều chỉnh endDate thành cuối ngày để tính đủ cả ngày hôm đó
        LocalDateTime adjustedEndDate = endDate.toLocalDate().atTime(23, 59, 59, 999999999);
        
        List<Object[]> results = paymentHistoryRepository.calculateRevenueByMonth(startDate, adjustedEndDate);
        
        return results.stream().map(result -> {
            BigDecimal totalRevenue = (BigDecimal) result[2];
            Long transactionCount = ((Number) result[3]).longValue();
            
            return RevenueResponse.builder()
                    .totalRevenue(totalRevenue)
                    .transactionCount(transactionCount)
                    .startDate(startDate)
                    .endDate(endDate)
                    .build();
        }).collect(Collectors.toList());
    }
    
    /**
     * Tính doanh thu theo tháng kèm danh sách doanh thu từng CLB
     */
    public List<RevenueByMonthWithClubsResponse> calculateRevenueByMonthWithClubs(
            LocalDateTime startDate, LocalDateTime endDate) {
        // Điều chỉnh endDate thành cuối ngày để tính đủ cả ngày hôm đó
        LocalDateTime adjustedEndDate = endDate.toLocalDate().atTime(23, 59, 59, 999999999);
        
        // Lấy dữ liệu doanh thu theo CLB và tháng
        List<Object[]> results = paymentHistoryRepository.calculateRevenueByClubAndMonth(
                startDate, adjustedEndDate);
        
        // Group by month (year, month) để tính tổng và danh sách CLB
        Map<String, RevenueByMonthWithClubsResponse> monthMap = new LinkedHashMap<>();
        
        for (Object[] result : results) {
            Integer year = ((Number) result[0]).intValue();
            Integer month = ((Number) result[1]).intValue();
            Integer clubId = ((Number) result[2]).intValue();
            String clubName = (String) result[3];
            BigDecimal revenue = (BigDecimal) result[4];
            Long transactionCount = ((Number) result[5]).longValue();
            
            // Tạo key cho tháng (year-month)
            String monthKey = year + "-" + month;
            
            // Lấy hoặc tạo response cho tháng này
            final Integer finalYear = year;
            final Integer finalMonth = month;
            RevenueByMonthWithClubsResponse monthResponse = monthMap.computeIfAbsent(monthKey, k -> 
                RevenueByMonthWithClubsResponse.builder()
                        .year(finalYear)
                        .month(finalMonth)
                        .totalRevenue(BigDecimal.ZERO)
                        .totalTransactionCount(0L)
                        .clubRevenues(new ArrayList<>())
                        .build()
            );
            
            // Cộng dồn tổng doanh thu và số giao dịch
            monthResponse.setTotalRevenue(monthResponse.getTotalRevenue().add(revenue));
            monthResponse.setTotalTransactionCount(
                    monthResponse.getTotalTransactionCount() + transactionCount);
            
            // Thêm doanh thu của CLB vào danh sách
            RevenueByMonthWithClubsResponse.ClubRevenueItem clubItem = 
                RevenueByMonthWithClubsResponse.ClubRevenueItem.builder()
                        .clubId(clubId)
                        .clubName(clubName)
                        .revenue(revenue)
                        .transactionCount(transactionCount)
                        .build();
            
            monthResponse.getClubRevenues().add(clubItem);
        }
        
        // Chuyển map thành list và sắp xếp theo tháng giảm dần
        return monthMap.values().stream()
                .sorted((a, b) -> {
                    int yearCompare = b.getYear().compareTo(a.getYear());
                    if (yearCompare != 0) return yearCompare;
                    return b.getMonth().compareTo(a.getMonth());
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Convert PaymentHistory entity sang PaymentHistoryResponse DTO
     */
    private PaymentHistoryResponse toPaymentHistoryResponse(PaymentHistory paymentHistory) {
        return PaymentHistoryResponse.builder()
                .paymentId(paymentHistory.getPaymentId())
                .subscriptionId(paymentHistory.getRegister().getSubscriptionId())
                .userId(paymentHistory.getUser().getUserId())
                .userName(paymentHistory.getUser().getFullName())
                .userEmail(paymentHistory.getUser().getEmail())
                .clubId(paymentHistory.getClub().getClubId())
                .clubName(paymentHistory.getClub().getClubName())
                .packageId(paymentHistory.getMembershipPackage().getPackageId())
                .packageName(paymentHistory.getMembershipPackage().getPackageName())
                .amount(paymentHistory.getAmount())
                .paymentMethod(paymentHistory.getPaymentMethod())
                .payosOrderCode(paymentHistory.getPayosOrderCode())
                .payosReference(paymentHistory.getPayosReference())
                .paymentDate(paymentHistory.getPaymentDate())
                .createdAt(paymentHistory.getCreatedAt())
                .build();
    }
}

