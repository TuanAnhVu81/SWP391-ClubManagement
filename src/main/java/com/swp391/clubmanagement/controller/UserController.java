package com.swp391.clubmanagement.controller;

import com.swp391.clubmanagement.dto.request.ForgotPasswordRequest;
import com.swp391.clubmanagement.dto.request.UserCreationRequest;
import com.swp391.clubmanagement.dto.request.UserUpdateRequest;
import com.swp391.clubmanagement.dto.request.VerifyEmailRequest;
import com.swp391.clubmanagement.dto.response.ApiResponse;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.exception.AppException;
import com.swp391.clubmanagement.service.UserService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserController {
    UserService userService;

    @PostMapping
    ApiResponse<Users> createUser(@RequestBody @Valid UserCreationRequest request) {
        return ApiResponse.<Users>builder()
                .result(userService.createUser(request))
                .build();
    }

    /**
     * API xác thực email qua link (GET request từ email)
     * Trả về trang HTML thông báo kết quả
     */
    @GetMapping(value = "/verify", produces = MediaType.TEXT_HTML_VALUE)
    String verifyEmailByToken(@RequestParam("token") String token) {
        try {
            userService.verifyEmailByToken(token);
            return buildSuccessHtmlPage();
        } catch (AppException e) {
            return buildErrorHtmlPage(e.getErrorCode().getMessage());
        }
    }

    @PostMapping("/verify")
    ApiResponse<String> verifyEmail(@RequestBody VerifyEmailRequest request) {
        userService.verifyEmail(request);
        return ApiResponse.<String>builder()
                .result("Xác thực email thành công")
                .build();
    }

    @PostMapping("/forgot-password")
    ApiResponse<String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        userService.forgotPassword(request);
        return ApiResponse.<String>builder()
                .result("Mật khẩu mới đã được gửi đến email của bạn")
                .build();
    }

    @GetMapping("/my-info")
    ApiResponse<Users> getMyInfo() {
        return ApiResponse.<Users>builder()
                .result(userService.getMyInfo())
                .build();
    }

    @PutMapping("/my-info")
    ApiResponse<Users> updateMyInfo(@RequestBody UserUpdateRequest request) {
        return ApiResponse.<Users>builder()
                .result(userService.updateUser(request))
                .build();
    }

    /**
     * Trang HTML thông báo xác thực thành công
     * (Đã đổi % thành %% để an toàn nếu sau này dùng .formatted)
     */
    private String buildSuccessHtmlPage() {
        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Xác Thực Email Thành Công - ClubHub</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                        min-height: 100vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        padding: 20px;
                    }
                    .container {
                        background: white;
                        border-radius: 20px;
                        padding: 60px 40px;
                        text-align: center;
                        box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                        max-width: 500px;
                        width: 100%%;
                    }
                    .icon {
                        width: 100px;
                        height: 100px;
                        background: linear-gradient(135deg, #11998e 0%%, #38ef7d 100%%);
                        border-radius: 50%%;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        margin: 0 auto 30px;
                        font-size: 50px;
                    }
                    h1 {
                        color: #333;
                        font-size: 28px;
                        margin-bottom: 15px;
                    }
                    p {
                        color: #666;
                        font-size: 16px;
                        line-height: 1.6;
                        margin-bottom: 30px;
                    }
                    .btn {
                        display: inline-block;
                        padding: 15px 40px;
                        background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                        color: white;
                        text-decoration: none;
                        border-radius: 50px;
                        font-weight: 600;
                        font-size: 16px;
                        transition: transform 0.3s, box-shadow 0.3s;
                    }
                    .btn:hover {
                        transform: translateY(-3px);
                        box-shadow: 0 10px 30px rgba(102, 126, 234, 0.4);
                    }
                    .logo {
                        margin-top: 40px;
                        color: #999;
                        font-size: 14px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="icon">✓</div>
                    <h1>Xác Thực Email Thành Công!</h1>
                    <p>Chúc mừng! Email của bạn đã được xác thực thành công.<br>Bây giờ bạn có thể đăng nhập và sử dụng đầy đủ các tính năng của ClubHub.</p>
                    <a href="http://localhost:3000/login" class="btn">Đăng Nhập Ngay</a>
                    <p class="logo">🎓 ClubHub - FPT University</p>
                </div>
            </body>
            </html>
            """;
    }

    /**
     * Trang HTML thông báo lỗi xác thực
     * FIX: Đã thay thế % bằng %% trong CSS
     */
    private String buildErrorHtmlPage(String errorMessage) {
        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Lỗi Xác Thực Email - ClubHub</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                        min-height: 100vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        padding: 20px;
                    }
                    .container {
                        background: white;
                        border-radius: 20px;
                        padding: 60px 40px;
                        text-align: center;
                        box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                        max-width: 500px;
                        width: 100%%;
                    }
                    .icon {
                        width: 100px;
                        height: 100px;
                        background: linear-gradient(135deg, #eb3349 0%%, #f45c43 100%%);
                        border-radius: 50%%;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        margin: 0 auto 30px;
                        font-size: 50px;
                    }
                    h1 {
                        color: #333;
                        font-size: 28px;
                        margin-bottom: 15px;
                    }
                    p {
                        color: #666;
                        font-size: 16px;
                        line-height: 1.6;
                        margin-bottom: 30px;
                    }
                    .error-box {
                        background: #fff5f5;
                        border: 1px solid #feb2b2;
                        border-radius: 10px;
                        padding: 15px 20px;
                        margin-bottom: 30px;
                        color: #c53030;
                        font-weight: 500;
                    }
                    .btn {
                        display: inline-block;
                        padding: 15px 40px;
                        background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                        color: white;
                        text-decoration: none;
                        border-radius: 50px;
                        font-weight: 600;
                        font-size: 16px;
                        transition: transform 0.3s, box-shadow 0.3s;
                    }
                    .btn:hover {
                        transform: translateY(-3px);
                        box-shadow: 0 10px 30px rgba(102, 126, 234, 0.4);
                    }
                    .logo {
                        margin-top: 40px;
                        color: #999;
                        font-size: 14px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="icon">✕</div>
                    <h1>Lỗi Xác Thực Email</h1>
                    <div class="error-box">%s</div>
                    <p>Link xác thực không hợp lệ hoặc đã hết hạn.<br>Vui lòng thử đăng ký lại hoặc liên hệ hỗ trợ.</p>
                    <a href="http://localhost:3000" class="btn">Quay Về Trang Chủ</a>
                    <p class="logo">🎓 ClubHub - FPT University</p>
                </div>
            </body>
            </html>
            """.formatted(errorMessage);
    }
}