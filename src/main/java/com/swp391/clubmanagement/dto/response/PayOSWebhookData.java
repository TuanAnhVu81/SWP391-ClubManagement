package com.swp391.clubmanagement.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PayOSWebhookData {
    String code;
    String desc;
    WebhookData data;
    String signature;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class WebhookData {
        Long orderCode;
        Integer amount;
        String description;
        String accountNumber;
        String reference;
        String transactionDateTime;
        String currency;
        String paymentLinkId;
        String code;
        String desc;
        String counterAccountBankId;
        String counterAccountBankName;
        String counterAccountName;
        String counterAccountNumber;
        String virtualAccountName;
        String virtualAccountNumber;
    }
}

