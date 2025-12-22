package com.swp391.clubmanagement.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * RevenueResponse - DTO cho response tính doanh thu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RevenueResponse {
    /** Tổng doanh thu */
    BigDecimal totalRevenue;
    
    /** Số lượng giao dịch */
    Long transactionCount;
    
    /** Khoảng thời gian bắt đầu */
    LocalDateTime startDate;
    
    /** Khoảng thời gian kết thúc */
    LocalDateTime endDate;
    
    /** ID CLB (nếu tính theo CLB) */
    Integer clubId;
    
    /** Tên CLB (nếu tính theo CLB) */
    String clubName;
    
    /** ID gói membership (nếu tính theo gói) */
    Integer packageId;
    
    /** Tên gói membership (nếu tính theo gói) */
    String packageName;
}

/**
 * RevenueByClubResponse - DTO cho doanh thu theo từng CLB
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
class RevenueByClubResponse {
    Integer clubId;
    String clubName;
    BigDecimal totalRevenue;
    Long transactionCount;
}

/**
 * RevenueByPackageResponse - DTO cho doanh thu theo từng gói
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
class RevenueByPackageResponse {
    Integer packageId;
    String packageName;
    BigDecimal totalRevenue;
    Long transactionCount;
}

/**
 * RevenueByTimeResponse - DTO cho doanh thu theo thời gian (ngày/tháng)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
class RevenueByTimeResponse {
    String period; // "2024-01" cho tháng, "2024-01-15" cho ngày
    Integer year;
    Integer month;
    Integer day;
    BigDecimal totalRevenue;
    Long transactionCount;
}

