package com.swp391.clubmanagement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * JoinClubRequest - DTO cho request đăng ký tham gia CLB
 * 
 * DTO này chứa thông tin để user đăng ký tham gia một CLB thông qua việc chọn gói membership.
 * Được sử dụng trong endpoint POST /registers/join để tạo đơn đăng ký mới.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JoinClubRequest {
    /** ID của gói membership muốn đăng ký (packageId trong bảng Memberships) */
    Integer packageId;
    
    /** Lý do gia nhập CLB: bắt buộc, từ 20-500 ký tự để đảm bảo chất lượng đơn đăng ký */
    @NotBlank(message = "Lý do gia nhập không được để trống")
    @Size(min = 20, max = 500, message = "Lý do gia nhập phải từ 20-500 ký tự")
    String joinReason;
}

