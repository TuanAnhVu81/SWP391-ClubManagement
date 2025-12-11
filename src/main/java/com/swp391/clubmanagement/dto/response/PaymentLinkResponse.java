package com.swp391.clubmanagement.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentLinkResponse {
    String paymentLink;
    String qrCode;
    Long orderCode;
    String paymentLinkId;
}

