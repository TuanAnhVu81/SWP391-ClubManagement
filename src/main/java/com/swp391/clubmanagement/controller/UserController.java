package com.swp391.clubmanagement.controller;

import com.swp391.clubmanagement.dto.request.*;
import com.swp391.clubmanagement.dto.response.ApiResponse;
import com.swp391.clubmanagement.dto.response.UserResponse;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.exception.AppException;
import com.swp391.clubmanagement.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "User Management", description = "APIs quản lý người dùng: Đăng ký, xác thực email, quên mật khẩu, cập nhật thông tin")
public class UserController {
    UserService userService;

    @PostMapping
    @Operation(summary = "Đăng ký tài khoản mới", 
               description = "Đăng ký tài khoản người dùng mới. Hệ thống sẽ gửi email xác thực đến địa chỉ email đã đăng ký.")
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
    @Operation(summary = "Xác thực email qua link", 
               description = "Xác thực email qua link được gửi trong email. API này được gọi khi người dùng click vào link xác thực trong email. Trả về trang HTML thông báo kết quả.")
    String verifyEmailByToken(@RequestParam("token") String token) {
        try {
            userService.verifyEmailByToken(token);
            return buildSuccessHtmlPage();
        } catch (AppException e) {
            return buildErrorHtmlPage(e.getErrorCode().getMessage());
        }
    }

    @PostMapping("/verify")
    @Operation(summary = "Xác thực email bằng mã code", 
               description = "Xác thực email bằng mã code được gửi đến email. Người dùng nhập mã code để xác thực tài khoản.")
    ApiResponse<String> verifyEmail(@RequestBody VerifyEmailRequest request) {
        userService.verifyEmail(request);
        return ApiResponse.<String>builder()
                .result("Xác thực email thành công")
                .build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Quên mật khẩu", 
               description = "Yêu cầu đặt lại mật khẩu. Hệ thống sẽ gửi mật khẩu mới đến email đã đăng ký.")
    ApiResponse<String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        userService.forgotPassword(request);
        return ApiResponse.<String>builder()
                .result("Mật khẩu mới đã được gửi đến email của bạn")
                .build();
    }

    @PostMapping("/change-password")
    @Operation(summary = "Đổi mật khẩu",
            description = "Đổi mật khẩu cho người dùng đang đăng nhập. Yêu cầu nhập mật khẩu cũ và mật khẩu mới.")
    ApiResponse<String> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        userService.changePassword(request);
        return ApiResponse.<String>builder()
                .result("Đổi mật khẩu thành công")
                .build();
    }

    @GetMapping("/my-info")
    @Operation(summary = "Xem thông tin cá nhân", 
               description = "Xem thông tin cá nhân của người dùng hiện tại (thông tin được lấy từ JWT token).")
    ApiResponse<UserResponse> getMyInfo() {
        return ApiResponse.<UserResponse>builder()
                .result(userService.getMyInfoResponse())
                .build();
    }

    @PutMapping("/my-info")
    @Operation(summary = "Cập nhật thông tin cá nhân", 
               description = "Cập nhật thông tin cá nhân của người dùng hiện tại (tên, số điện thoại, avatar, v.v.).")
    ApiResponse<UserResponse> updateMyInfo(@RequestBody UserUpdateRequest request) {
        Users user = userService.updateUser(request);
        return ApiResponse.<UserResponse>builder()
                .result(userService.getMyInfoResponse())
                .build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_QuanTriVien')") // Chỉ Admin được xem
    @Operation(summary = "Lấy danh sách Users (Phân trang)",
            description = "Lấy danh sách toàn bộ người dùng. Chỉ dành cho Admin. Hỗ trợ phân trang và sắp xếp.")
    public ApiResponse<Page<UserResponse>> getAllUsers(
            @ParameterObject
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ApiResponse.<Page<UserResponse>>builder()
                .result(userService.getAllUsers(pageable))
                .build();
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('SCOPE_QuanTriVien')") // Chỉ Admin được xóa
    @Operation(summary = "Xóa user (Admin only)",
            description = "Xóa (deactivate) tài khoản người dùng. Chỉ dành cho Admin. Đây là soft delete - user sẽ được đánh dấu là inactive nhưng dữ liệu vẫn được giữ lại. Không thể xóa user đang là founder của CLB.")
    public ApiResponse<Void> deleteUser(@PathVariable String userId) {
        userService.deleteUser(userId);
        return ApiResponse.<Void>builder()
                .message("Xóa user thành công")
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