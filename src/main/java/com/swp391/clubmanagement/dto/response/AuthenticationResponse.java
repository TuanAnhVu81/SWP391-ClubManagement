package com.swp391.clubmanagement.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * AuthenticationResponse - DTO cho response đăng nhập
 * 
 * DTO này chứa kết quả của quá trình xác thực:
 * - token: JWT token để xác thực các request tiếp theo
 * - authenticated: Trạng thái xác thực thành công hay không
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthenticationResponse {
    /** JWT token để xác thực các request tiếp theo (được gửi trong header Authorization) */
    String token;
    
    /** Trạng thái xác thực: true = đăng nhập thành công, false = thất bại */
    boolean authenticated;
}

