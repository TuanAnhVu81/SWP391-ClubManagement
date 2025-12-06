package com.swp391.clubmanagement.dto.request;

import com.swp391.clubmanagement.enums.ClubCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubApplicationRequest {
    
    @NotBlank(message = "Tên CLB không được để trống")
    String proposedName;
    
    @NotNull(message = "Danh mục CLB không được để trống")
    ClubCategory category;
    
    @NotBlank(message = "Mục đích thành lập CLB không được để trống")
    String purpose;
}
