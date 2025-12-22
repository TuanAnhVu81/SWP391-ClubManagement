package com.swp391.clubmanagement.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PaymentHistoryResponse - DTO cho response lịch sử giao dịch
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentHistoryResponse {
    /** ID của giao dịch */
    Integer paymentId;
    
    /** ID của đơn đăng ký */
    Integer subscriptionId;
    
    /** ID của user thanh toán */
    String userId;
    
    /** Tên user thanh toán */
    String userName;
    
    /** Email user */
    String userEmail;
    
    /** ID của CLB */
    Integer clubId;
    
    /** Tên CLB */
    String clubName;
    
    /** ID của gói membership */
    Integer packageId;
    
    /** Tên gói membership */
    String packageName;
    
    /** Số tiền thanh toán */
    BigDecimal amount;
    
    /** Phương thức thanh toán */
    String paymentMethod;
    
    /** Mã đơn hàng PayOS */
    Long payosOrderCode;
    
    /** Reference code từ PayOS */
    String payosReference;
    
    /** Ngày giờ thanh toán */
    LocalDateTime paymentDate;
    
    /** Thời điểm record được tạo */
    LocalDateTime createdAt;
}

