package com.swp391.clubmanagement.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PayOSPaymentLinkResponse {
    String code;
    String desc;
    PaymentData data;
    String signature;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class PaymentData {
        String bin;
        String accountNumber;
        String accountName;
        Integer amount;
        String description;
        Long orderCode;
        String currency;
        String paymentLinkId;
        String qrCode;
    }
}

