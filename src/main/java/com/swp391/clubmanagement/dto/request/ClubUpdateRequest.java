package com.swp391.clubmanagement.dto.request;

import jakarta.validation.constraints.Email;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubUpdateRequest {
    
    String logo; // URL của logo CLB
    
    String description; // Mô tả CLB
    
    String location; // Địa điểm sinh hoạt

    @Email(message = "Email không hợp lệ")
    String email; // Email liên hệ của CLB
}
