package com.swp391.clubmanagement.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * AuthenticationRequest - DTO cho request đăng nhập
 * 
 * DTO này chứa thông tin đăng nhập của user: email và mật khẩu.
 * Được sử dụng trong endpoint POST /auth/login để xác thực người dùng.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthenticationRequest {
    /** Email đăng nhập của user */
    String email;
    
    /** Mật khẩu của user (chưa mã hóa) */
    String password;
}

