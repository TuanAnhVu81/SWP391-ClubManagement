package com.swp391.clubmanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConfirmWebhookResponse {
    
    String code;
    
    @JsonProperty("desc")
    String description;
    
    String data;
    
    String signature;
}

