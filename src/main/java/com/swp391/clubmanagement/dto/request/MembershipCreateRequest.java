package com.swp391.clubmanagement.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MembershipCreateRequest {
    
    @NotBlank(message = "Tên gói không được để trống")
    String packageName;
    
    @NotBlank(message = "Kỳ hạn không được để trống")
    String term; // Kỳ hạn (VD: "1 tháng", "3 tháng", "6 tháng", "1 năm")
    
    @NotNull(message = "Giá không được để trống")
    @DecimalMin(value = "0.0", message = "Giá phải lớn hơn hoặc bằng 0")
    BigDecimal price;
    
    String description; // Mô tả gói (optional)
}
