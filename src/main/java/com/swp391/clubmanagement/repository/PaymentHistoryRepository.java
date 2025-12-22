package com.swp391.clubmanagement.repository;

import com.swp391.clubmanagement.entity.Clubs;
import com.swp391.clubmanagement.entity.PaymentHistory;
import com.swp391.clubmanagement.entity.Users;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * PaymentHistoryRepository - Spring Data JPA Repository cho entity PaymentHistory
 * 
 * Interface này cung cấp các method để:
 * - Tìm kiếm lịch sử giao dịch theo user, club, thời gian
 * - Tính doanh thu theo nhiều tiêu chí khác nhau
 */
@Repository
public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, Integer> {
    
    // ============ TÌM KIẾM LỊCH SỬ GIAO DỊCH ============
    
    /** Tìm tất cả giao dịch của một user */
    List<PaymentHistory> findByUser(Users user);
    
    /** Tìm giao dịch của user với phân trang */
    Page<PaymentHistory> findByUser(Users user, Pageable pageable);
    
    /** Tìm tất cả giao dịch của một CLB */
    List<PaymentHistory> findByClub(Clubs club);
    
    /** Tìm giao dịch của CLB với phân trang */
    Page<PaymentHistory> findByClub(Clubs club, Pageable pageable);
    
    /** Tìm giao dịch trong khoảng thời gian */
    List<PaymentHistory> findByPaymentDateBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    /** Tìm giao dịch của user trong khoảng thời gian */
    List<PaymentHistory> findByUserAndPaymentDateBetween(Users user, LocalDateTime startDate, LocalDateTime endDate);
    
    /** Tìm giao dịch của CLB trong khoảng thời gian */
    List<PaymentHistory> findByClubAndPaymentDateBetween(Clubs club, LocalDateTime startDate, LocalDateTime endDate);
    
    /** Tìm giao dịch theo PayOS order code */
    Optional<PaymentHistory> findByPayosOrderCode(Long orderCode);
    
    // ============ TÍNH DOANH THU ============
    
    /** Tính tổng doanh thu của một CLB */
    @Query("SELECT COALESCE(SUM(ph.amount), 0) FROM PaymentHistory ph WHERE ph.club.clubId = :clubId")
    BigDecimal calculateTotalRevenueByClub(@Param("clubId") Integer clubId);
    
    /** Tính tổng doanh thu của một CLB trong khoảng thời gian */
    @Query("SELECT COALESCE(SUM(ph.amount), 0) FROM PaymentHistory ph WHERE ph.club.clubId = :clubId " +
           "AND ph.paymentDate BETWEEN :startDate AND :endDate")
    BigDecimal calculateRevenueByClubAndDateRange(
            @Param("clubId") Integer clubId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    /** Tính tổng doanh thu của một gói membership */
    @Query("SELECT COALESCE(SUM(ph.amount), 0) FROM PaymentHistory ph WHERE ph.membershipPackage.packageId = :packageId")
    BigDecimal calculateTotalRevenueByPackage(@Param("packageId") Integer packageId);
    
    /** Tính tổng doanh thu của một gói membership trong khoảng thời gian */
    @Query("SELECT COALESCE(SUM(ph.amount), 0) FROM PaymentHistory ph WHERE ph.membershipPackage.packageId = :packageId " +
           "AND ph.paymentDate BETWEEN :startDate AND :endDate")
    BigDecimal calculateRevenueByPackageAndDateRange(
            @Param("packageId") Integer packageId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    /** Tính tổng doanh thu của tất cả CLB trong khoảng thời gian */
    @Query("SELECT COALESCE(SUM(ph.amount), 0) FROM PaymentHistory ph WHERE ph.paymentDate BETWEEN :startDate AND :endDate")
    BigDecimal calculateTotalRevenueByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    /** Tính doanh thu theo từng CLB (group by club) */
    @Query("SELECT ph.club.clubId, ph.club.clubName, COALESCE(SUM(ph.amount), 0) as totalRevenue " +
           "FROM PaymentHistory ph " +
           "WHERE ph.paymentDate BETWEEN :startDate AND :endDate " +
           "GROUP BY ph.club.clubId, ph.club.clubName " +
           "ORDER BY totalRevenue DESC")
    List<Object[]> calculateRevenueByClubGrouped(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    /** Tính doanh thu theo từng gói membership của một CLB */
    @Query("SELECT ph.membershipPackage.packageId, ph.membershipPackage.packageName, " +
           "COALESCE(SUM(ph.amount), 0) as totalRevenue, COUNT(ph) as transactionCount " +
           "FROM PaymentHistory ph " +
           "WHERE ph.club.clubId = :clubId AND ph.paymentDate BETWEEN :startDate AND :endDate " +
           "GROUP BY ph.membershipPackage.packageId, ph.membershipPackage.packageName " +
           "ORDER BY totalRevenue DESC")
    List<Object[]> calculateRevenueByPackageForClub(
            @Param("clubId") Integer clubId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    /** Tính doanh thu theo tháng */
    @Query("SELECT YEAR(ph.paymentDate) as year, MONTH(ph.paymentDate) as month, " +
           "COALESCE(SUM(ph.amount), 0) as totalRevenue, COUNT(ph) as transactionCount " +
           "FROM PaymentHistory ph " +
           "WHERE ph.paymentDate BETWEEN :startDate AND :endDate " +
           "GROUP BY YEAR(ph.paymentDate), MONTH(ph.paymentDate) " +
           "ORDER BY year DESC, month DESC")
    List<Object[]> calculateRevenueByMonth(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    /** Tính doanh thu của một CLB theo tháng */
    @Query("SELECT YEAR(ph.paymentDate) as year, MONTH(ph.paymentDate) as month, " +
           "COALESCE(SUM(ph.amount), 0) as totalRevenue, COUNT(ph) as transactionCount " +
           "FROM PaymentHistory ph " +
           "WHERE ph.club.clubId = :clubId AND ph.paymentDate BETWEEN :startDate AND :endDate " +
           "GROUP BY YEAR(ph.paymentDate), MONTH(ph.paymentDate) " +
           "ORDER BY year DESC, month DESC")
    List<Object[]> calculateRevenueByClubByMonth(
            @Param("clubId") Integer clubId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    /** Tính doanh thu theo ngày */
    @Query("SELECT DATE(ph.paymentDate) as date, " +
           "COALESCE(SUM(ph.amount), 0) as totalRevenue, COUNT(ph) as transactionCount " +
           "FROM PaymentHistory ph " +
           "WHERE ph.paymentDate BETWEEN :startDate AND :endDate " +
           "GROUP BY DATE(ph.paymentDate) " +
           "ORDER BY date DESC")
    List<Object[]> calculateRevenueByDay(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    /** Đếm số giao dịch của một CLB */
    long countByClub(Clubs club);
    
    /** Đếm số giao dịch của một CLB trong khoảng thời gian */
    long countByClubAndPaymentDateBetween(Clubs club, LocalDateTime startDate, LocalDateTime endDate);
}

