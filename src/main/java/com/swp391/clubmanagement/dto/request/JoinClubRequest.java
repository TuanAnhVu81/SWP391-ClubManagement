package com.swp391.clubmanagement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JoinClubRequest {
    Integer packageId;
    
    @NotBlank(message = "Lý do gia nhập không được để trống")
    @Size(min = 20, max = 500, message = "Lý do gia nhập phải từ 20-500 ký tự")
    String joinReason; // Lý do tại sao muốn gia nhập CLB
}

