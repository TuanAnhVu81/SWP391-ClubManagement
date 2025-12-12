package com.swp391.clubmanagement.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConfirmWebhookRequest {
    
    @NotBlank(message = "Webhook URL không được để trống")
    String webhookUrl;
}

