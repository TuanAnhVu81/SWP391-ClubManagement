package com.swp391.clubmanagement.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * PaymentLinkResponse - DTO cho response link thanh toán PayOS
 * 
 * DTO này chứa thông tin về link thanh toán được tạo từ PayOS:
 * - Link thanh toán và QR code để user quét
 * - Order code và Payment Link ID để tra cứu giao dịch
 * 
 * Được sử dụng trong endpoint POST /payments/create-link sau khi tạo thành công link thanh toán.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentLinkResponse {
    /** Link thanh toán: User click vào link này để thanh toán */
    String paymentLink;
    
    /** Mã QR code: User quét bằng app ngân hàng để thanh toán */
    String qrCode;
    
    /** Mã đơn hàng PayOS: Dùng để tra cứu giao dịch trên PayOS */
    Long orderCode;
    
    /** ID của payment link trong hệ thống PayOS */
    String paymentLinkId;
}

