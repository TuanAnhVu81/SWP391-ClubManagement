package com.swp391.clubmanagement.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;

/**
 * RevenueByMonthWithClubsResponse - DTO cho doanh thu theo tháng kèm danh sách doanh thu từng CLB
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RevenueByMonthWithClubsResponse {
    /** Năm */
    Integer year;
    
    /** Tháng */
    Integer month;
    
    /** Tổng doanh thu của tất cả CLB trong tháng này */
    BigDecimal totalRevenue;
    
    /** Tổng số giao dịch của tất cả CLB trong tháng này */
    Long totalTransactionCount;
    
    /** Danh sách doanh thu theo từng CLB */
    List<ClubRevenueItem> clubRevenues;
    
    /**
     * ClubRevenueItem - DTO cho doanh thu của một CLB trong tháng
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ClubRevenueItem {
        Integer clubId;
        String clubName;
        BigDecimal revenue;
        Long transactionCount;
    }
}

