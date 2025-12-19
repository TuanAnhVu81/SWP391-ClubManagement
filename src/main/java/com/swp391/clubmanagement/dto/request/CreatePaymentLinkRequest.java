package com.swp391.clubmanagement.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * CreatePaymentLinkRequest - DTO cho request tạo link thanh toán PayOS
 * 
 * DTO này chứa ID của đơn đăng ký cần tạo link thanh toán.
 * Được sử dụng trong endpoint POST /payments/create-link để tạo link thanh toán từ PayOS.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreatePaymentLinkRequest {
    /** ID của đơn đăng ký (subscription_id) cần tạo link thanh toán: bắt buộc */
    @NotNull(message = "Subscription ID is required")
    Integer subscriptionId;
}

