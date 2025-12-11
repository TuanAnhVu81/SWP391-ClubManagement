package com.swp391.clubmanagement.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL) // Bỏ qua các field null khi serialize JSON
public class PayOSCreatePaymentRequest {
    Integer orderCode;
    Integer amount;
    String description;
    String returnUrl;
    String cancelUrl;
    Long expiredAt;
}

