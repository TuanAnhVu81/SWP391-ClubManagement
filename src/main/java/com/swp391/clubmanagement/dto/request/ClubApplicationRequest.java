package com.swp391.clubmanagement.dto.request;

import com.swp391.clubmanagement.enums.ClubCategory;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

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
    
    String description; // Mô tả chi tiết về CLB
    
    @NotBlank(message = "Địa điểm sinh hoạt không được để trống")
    String location; // Địa điểm sinh hoạt của CLB
    
    @NotBlank(message = "Email CLB không được để trống")
    @Email(message = "Email không đúng định dạng")
    String email; // Email liên hệ của CLB
    
    @NotNull(message = "Phí thành viên mặc định không được để trống")
    @DecimalMin(value = "0.0", message = "Phí thành viên phải lớn hơn hoặc bằng 0")
    BigDecimal defaultMembershipFee; // Phí cho gói thành viên mặc định (sẽ tự động tạo khi duyệt)
}
