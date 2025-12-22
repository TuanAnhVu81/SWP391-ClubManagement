package com.swp391.clubmanagement.service;

import com.swp391.clubmanagement.dto.response.PaymentHistoryResponse;
import com.swp391.clubmanagement.dto.response.RevenueResponse;
import com.swp391.clubmanagement.entity.Clubs;
import com.swp391.clubmanagement.entity.Memberships;
import com.swp391.clubmanagement.entity.PaymentHistory;
import com.swp391.clubmanagement.entity.Registers;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.exception.AppException;
import com.swp391.clubmanagement.exception.ErrorCode;
import com.swp391.clubmanagement.repository.ClubRepository;
import com.swp391.clubmanagement.repository.MembershipRepository;
import com.swp391.clubmanagement.repository.PaymentHistoryRepository;
import com.swp391.clubmanagement.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PaymentHistoryService - Service xử lý lịch sử giao dịch và tính doanh thu
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PaymentHistoryService {
    
    PaymentHistoryRepository paymentHistoryRepository;
    UserRepository userRepository;
    ClubRepository clubRepository;
    MembershipRepository membershipRepository;
    
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
     * Xem lịch sử giao dịch của một user (Admin/Leader)
     */
    public Page<PaymentHistoryResponse> getUserPaymentHistory(String userId, Pageable pageable) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        Page<PaymentHistory> paymentHistoryPage = paymentHistoryRepository.findByUser(user, pageable);
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
     * Xem lịch sử giao dịch trong khoảng thời gian
     */
    public List<PaymentHistoryResponse> getPaymentHistoryByDateRange(
            LocalDateTime startDate, LocalDateTime endDate) {
        List<PaymentHistory> paymentHistoryList = paymentHistoryRepository.findByPaymentDateBetween(startDate, endDate);
        return paymentHistoryList.stream()
                .map(this::toPaymentHistoryResponse)
                .collect(Collectors.toList());
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
     * Tính doanh thu của một CLB trong khoảng thời gian
     */
    public RevenueResponse calculateRevenueByClubAndDateRange(
            Integer clubId, LocalDateTime startDate, LocalDateTime endDate) {
        Clubs club = clubRepository.findById(clubId)
                .orElseThrow(() -> new AppException(ErrorCode.CLUB_NOT_FOUND));
        
        BigDecimal totalRevenue = paymentHistoryRepository.calculateRevenueByClubAndDateRange(
                clubId, startDate, endDate);
        Long transactionCount = paymentHistoryRepository.countByClubAndPaymentDateBetween(
                club, startDate, endDate);
        
        return RevenueResponse.builder()
                .totalRevenue(totalRevenue)
                .transactionCount(transactionCount)
                .startDate(startDate)
                .endDate(endDate)
                .clubId(clubId)
                .clubName(club.getClubName())
                .build();
    }
    
    /**
     * Tính doanh thu của một gói membership
     */
    public RevenueResponse calculateRevenueByPackage(Integer packageId) {
        Memberships membership = membershipRepository.findById(packageId)
                .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_NOT_FOUND));
        
        BigDecimal totalRevenue = paymentHistoryRepository.calculateTotalRevenueByPackage(packageId);
        
        return RevenueResponse.builder()
                .totalRevenue(totalRevenue)
                .packageId(packageId)
                .packageName(membership.getPackageName())
                .build();
    }
    
    /**
     * Tính doanh thu của một gói membership trong khoảng thời gian
     */
    public RevenueResponse calculateRevenueByPackageAndDateRange(
            Integer packageId, LocalDateTime startDate, LocalDateTime endDate) {
        Memberships membership = membershipRepository.findById(packageId)
                .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_NOT_FOUND));
        
        BigDecimal totalRevenue = paymentHistoryRepository.calculateRevenueByPackageAndDateRange(
                packageId, startDate, endDate);
        
        return RevenueResponse.builder()
                .totalRevenue(totalRevenue)
                .startDate(startDate)
                .endDate(endDate)
                .packageId(packageId)
                .packageName(membership.getPackageName())
                .build();
    }
    
    /**
     * Tính tổng doanh thu trong khoảng thời gian
     */
    public RevenueResponse calculateTotalRevenueByDateRange(
            LocalDateTime startDate, LocalDateTime endDate) {
        BigDecimal totalRevenue = paymentHistoryRepository.calculateTotalRevenueByDateRange(startDate, endDate);
        List<PaymentHistory> paymentHistoryList = paymentHistoryRepository.findByPaymentDateBetween(startDate, endDate);
        
        return RevenueResponse.builder()
                .totalRevenue(totalRevenue)
                .transactionCount((long) paymentHistoryList.size())
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }
    
    /**
     * Tính doanh thu theo từng CLB (group by club)
     */
    public List<RevenueResponse> calculateRevenueByClubGrouped(
            LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> results = paymentHistoryRepository.calculateRevenueByClubGrouped(startDate, endDate);
        
        return results.stream().map(result -> {
            Integer clubId = (Integer) result[0];
            String clubName = (String) result[1];
            BigDecimal totalRevenue = (BigDecimal) result[2];
            
            return RevenueResponse.builder()
                    .clubId(clubId)
                    .clubName(clubName)
                    .totalRevenue(totalRevenue)
                    .startDate(startDate)
                    .endDate(endDate)
                    .build();
        }).collect(Collectors.toList());
    }
    
    /**
     * Tính doanh thu theo từng gói membership của một CLB
     */
    public List<RevenueResponse> calculateRevenueByPackageForClub(
            Integer clubId, LocalDateTime startDate, LocalDateTime endDate) {
        Clubs club = clubRepository.findById(clubId)
                .orElseThrow(() -> new AppException(ErrorCode.CLUB_NOT_FOUND));
        
        List<Object[]> results = paymentHistoryRepository.calculateRevenueByPackageForClub(
                clubId, startDate, endDate);
        
        return results.stream().map(result -> {
            Integer packageId = (Integer) result[0];
            String packageName = (String) result[1];
            BigDecimal totalRevenue = (BigDecimal) result[2];
            Long transactionCount = ((Number) result[3]).longValue();
            
            return RevenueResponse.builder()
                    .clubId(clubId)
                    .clubName(club.getClubName())
                    .packageId(packageId)
                    .packageName(packageName)
                    .totalRevenue(totalRevenue)
                    .transactionCount(transactionCount)
                    .startDate(startDate)
                    .endDate(endDate)
                    .build();
        }).collect(Collectors.toList());
    }
    
    /**
     * Tính doanh thu theo tháng
     */
    public List<RevenueResponse> calculateRevenueByMonth(
            LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> results = paymentHistoryRepository.calculateRevenueByMonth(startDate, endDate);
        
        return results.stream().map(result -> {
            Integer year = ((Number) result[0]).intValue();
            Integer month = ((Number) result[1]).intValue();
            BigDecimal totalRevenue = (BigDecimal) result[2];
            Long transactionCount = ((Number) result[3]).longValue();
            
            String period = String.format("%04d-%02d", year, month);
            
            return RevenueResponse.builder()
                    .totalRevenue(totalRevenue)
                    .transactionCount(transactionCount)
                    .startDate(startDate)
                    .endDate(endDate)
                    .build();
        }).collect(Collectors.toList());
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

