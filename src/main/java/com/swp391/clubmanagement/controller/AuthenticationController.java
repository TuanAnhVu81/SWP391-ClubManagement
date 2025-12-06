package com.swp391.clubmanagement.controller;

import com.nimbusds.jose.JOSEException;
import com.swp391.clubmanagement.dto.request.AuthenticationRequest;
import com.swp391.clubmanagement.dto.request.IntrospectRequest;
import com.swp391.clubmanagement.dto.request.LogoutRequest;
import com.swp391.clubmanagement.dto.response.ApiResponse;
import com.swp391.clubmanagement.dto.response.AuthenticationResponse;
import com.swp391.clubmanagement.dto.response.IntrospectResponse;
import com.swp391.clubmanagement.service.AuthenticationService;
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
public class AuthenticationController {
    AuthenticationService authenticationService;

    /**
     * API Đăng nhập.
     * Endpoint: POST /auth/token
     * @param request chứa email và password
     * @return Token JWT nếu đăng nhập thành công
     */
    @PostMapping("/token")
    ApiResponse<AuthenticationResponse> authenticate(@RequestBody AuthenticationRequest request) {
        var result = authenticationService.authenticate(request);
        return ApiResponse.<AuthenticationResponse>builder()
                .result(result)
                .build();
    }

    /**
     * API Kiểm tra token (Introspect).
     * Endpoint: POST /auth/introspect
     * @param request chứa token cần kiểm tra
     * @return valid: true/false
     */
    @PostMapping("/introspect")
    ApiResponse<IntrospectResponse> authenticate(@RequestBody IntrospectRequest request)
            throws ParseException, JOSEException {
        var result = authenticationService.introspect(request);
        return ApiResponse.<IntrospectResponse>builder()
                .result(result)
                .build();
    }

    /**
     * API Đăng xuất.
     * Endpoint: POST /auth/logout
     * @param request chứa token cần đăng xuất
     */
    @PostMapping("/logout")
    ApiResponse<Void> logout(@RequestBody LogoutRequest request)
            throws ParseException, JOSEException {
        authenticationService.logout(request);
        return ApiResponse.<Void>builder()
                .build();
    }
}

