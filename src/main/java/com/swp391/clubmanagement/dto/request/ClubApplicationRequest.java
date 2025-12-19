package com.swp391.clubmanagement.dto.request;

import com.swp391.clubmanagement.enums.ClubCategory;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

/**
 * ClubApplicationRequest - DTO cho request tạo đơn xin thành lập CLB
 * 
 * DTO này chứa thông tin để user gửi đơn xin thành lập CLB mới.
 * Tất cả các trường đều có validation để đảm bảo dữ liệu hợp lệ.
 * Được sử dụng trong endpoint POST /club-applications để tạo đơn mới.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubApplicationRequest {
    /** Tên CLB đề xuất: bắt buộc, không được để trống */
    @NotBlank(message = "Tên CLB không được để trống")
    String proposedName;
    
    /** Danh mục CLB: bắt buộc, phải chọn một trong các giá trị của ClubCategory */
    @NotNull(message = "Danh mục CLB không được để trống")
    ClubCategory category;
    
    /** Mục đích thành lập CLB: bắt buộc, không được để trống */
    @NotBlank(message = "Mục đích thành lập CLB không được để trống")
    String purpose;
    
    /** Mô tả chi tiết về CLB: không bắt buộc */
    String description;
    
    /** Địa điểm sinh hoạt của CLB: bắt buộc, không được để trống */
    @NotBlank(message = "Địa điểm sinh hoạt không được để trống")
    String location;
    
    /** Email liên hệ của CLB: bắt buộc, phải đúng định dạng email */
    @NotBlank(message = "Email CLB không được để trống")
    @Email(message = "Email không đúng định dạng")
    String email;
    
    /** Phí thành viên mặc định: bắt buộc, >= 0. Khi Admin duyệt đơn, sẽ tự động tạo gói membership với giá này */
    @NotNull(message = "Phí thành viên mặc định không được để trống")
    @DecimalMin(value = "0.0", message = "Phí thành viên phải lớn hơn hoặc bằng 0")
    BigDecimal defaultMembershipFee;
}
