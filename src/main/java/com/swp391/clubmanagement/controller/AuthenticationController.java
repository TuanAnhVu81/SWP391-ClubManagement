package com.swp391.clubmanagement.controller;

import com.nimbusds.jose.JOSEException;
import com.swp391.clubmanagement.dto.request.AuthenticationRequest;
import com.swp391.clubmanagement.dto.request.IntrospectRequest;
import com.swp391.clubmanagement.dto.request.LogoutRequest;
import com.swp391.clubmanagement.dto.response.ApiResponse;
import com.swp391.clubmanagement.dto.response.AuthenticationResponse;
import com.swp391.clubmanagement.dto.response.IntrospectResponse;
import com.swp391.clubmanagement.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.ParseException;

/**
 * AuthenticationController: REST Controller xử lý các yêu cầu xác thực.
 * Expose API tại: /api/auth/...
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Authentication", description = "APIs xác thực: Đăng nhập, đăng xuất, kiểm tra token")
public class AuthenticationController {
    AuthenticationService authenticationService;

    /**
     * POST /api/auth/token
     * Đăng nhập vào hệ thống
     * 
     * @param request { "email": "user@example.com", "password": "password123" }
     * @return JWT token để sử dụng cho các API khác
     */
    @PostMapping("/token")
    @Operation(summary = "Đăng nhập", 
               description = "Đăng nhập vào hệ thống bằng email và password. Trả về JWT token để sử dụng cho các API khác.")
    public ApiResponse<AuthenticationResponse> authenticate(@Valid @RequestBody AuthenticationRequest request) {
        var result = authenticationService.authenticate(request);
        return ApiResponse.<AuthenticationResponse>builder()
                .result(result)
                .build();
    }

    /**
     * POST /api/auth/introspect
     * Kiểm tra token có hợp lệ không
     * 
     * @param request { "token": "eyJhbGciOiJIUzUxMiJ9..." }
     * @return { "valid": true/false }
     */
    @PostMapping("/introspect")
    @Operation(summary = "Kiểm tra token", 
               description = "Kiểm tra token JWT có hợp lệ hay không (chữ ký đúng và chưa hết hạn)")
    public ApiResponse<IntrospectResponse> introspect(@Valid @RequestBody IntrospectRequest request)
            throws ParseException, JOSEException {
        var result = authenticationService.introspect(request);
        return ApiResponse.<IntrospectResponse>builder()
                .result(result)
                .build();
    }

    /**
     * POST /api/auth/logout
     * Đăng xuất khỏi hệ thống
     * 
     * @param request { "token": "eyJhbGciOiJIUzUxMiJ9..." }
     * @return Thành công
     */
    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất", 
               description = "Đăng xuất khỏi hệ thống. Token sẽ được invalidate (trong tương lai sẽ thêm blacklist).")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request)
            throws ParseException, JOSEException {
        authenticationService.logout(request);
        return ApiResponse.<Void>builder()
                .build();
    }
}

