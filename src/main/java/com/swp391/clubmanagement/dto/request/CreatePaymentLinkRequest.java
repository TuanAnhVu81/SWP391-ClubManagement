package com.swp391.clubmanagement.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreatePaymentLinkRequest {
    @NotNull(message = "Subscription ID is required")
    Integer subscriptionId;
}

