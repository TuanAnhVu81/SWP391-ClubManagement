package com.swp391.clubmanagement.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * UserCreationRequest - DTO cho request tạo user mới (đăng ký tài khoản)
 * 
 * DTO này chứa thông tin để tạo một user mới trong hệ thống.
 * Được sử dụng trong endpoint POST /users để đăng ký tài khoản.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserCreationRequest {
    /** Mã sinh viên: phải duy nhất trong hệ thống */
    String studentCode;
    
    /** Họ và tên đầy đủ */
    String fullName;
    
    /** Email: dùng để đăng nhập và nhận mã xác thực */
    String email;
    
    /** Mật khẩu: sẽ được mã hóa trước khi lưu vào database */
    String password;
    
    /** Số điện thoại (không bắt buộc) */
    String phoneNumber;
    
    /** Chuyên ngành học (không bắt buộc) */
    String major;
}
