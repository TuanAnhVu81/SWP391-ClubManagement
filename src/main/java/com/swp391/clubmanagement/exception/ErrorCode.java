package com.swp391.clubmanagement.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * ErrorCode Enum: Định nghĩa tập trung tất cả các mã lỗi trong hệ thống.
 * Giúp quản lý lỗi thống nhất, dễ bảo trì và mở rộng.
 */
@Getter
public enum ErrorCode {
    // --- Success Code (Mã thành công) ---
    SUCCESS(1000, "Success", HttpStatus.OK),
    
    // --- General Errors (Lỗi chung hệ thống - 1xxx) ---
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR), // Lỗi chưa được định nghĩa
    INVALID_KEY(1001, "Invalid message key", HttpStatus.BAD_REQUEST), // Key message không hợp lệ (thường dùng cho i18n)
    
    // --- User Related Errors (Lỗi liên quan đến người dùng - 2xxx) ---
    USER_EXISTED(2001, "User already exists", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(2002, "User not found", HttpStatus.NOT_FOUND),
    USERNAME_INVALID(2003, "Username must be at least 3 characters", HttpStatus.BAD_REQUEST),
    PASSWORD_INVALID(2004, "Password must be at least 8 characters", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL(2005, "Invalid email format", HttpStatus.BAD_REQUEST),
    
    // --- Authentication & Authorization Errors (Lỗi xác thực & phân quyền - 3xxx) ---
    UNAUTHENTICATED(3001, "Unauthenticated", HttpStatus.UNAUTHORIZED), // Chưa đăng nhập hoặc token không hợp lệ
    UNAUTHORIZED(3002, "You do not have permission", HttpStatus.FORBIDDEN), // Đã đăng nhập nhưng không có quyền truy cập
    INVALID_TOKEN(3003, "Invalid token", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(3004, "Token has expired", HttpStatus.UNAUTHORIZED),
    
    // --- Club Related Errors (Lỗi liên quan đến CLB - 4xxx) ---
    CLUB_NOT_FOUND(4001, "Club not found", HttpStatus.NOT_FOUND),
    CLUB_EXISTED(4002, "Club already exists", HttpStatus.BAD_REQUEST),
    CLUB_NAME_INVALID(4003, "Club name is invalid", HttpStatus.BAD_REQUEST),
    
    // --- Membership Related Errors (Lỗi thành viên - 5xxx) ---
    MEMBERSHIP_NOT_FOUND(5001, "Membership not found", HttpStatus.NOT_FOUND),
    ALREADY_MEMBER(5002, "Already a member of this club", HttpStatus.BAD_REQUEST),
    NOT_CLUB_MEMBER(5003, "Not a member of this club", HttpStatus.FORBIDDEN),
    
    // --- Application Related Errors (Lỗi đơn đăng ký - 6xxx) ---
    APPLICATION_NOT_FOUND(6001, "Application not found", HttpStatus.NOT_FOUND),
    APPLICATION_ALREADY_SUBMITTED(6002, "Application already submitted", HttpStatus.BAD_REQUEST),
    INVALID_APPLICATION_STATUS(6003, "Invalid application status", HttpStatus.BAD_REQUEST),
    APPLICATION_ALREADY_REVIEWED(6004, "Application already reviewed", HttpStatus.BAD_REQUEST),
    NOT_CLUB_LEADER(6005, "You are not the leader of this club", HttpStatus.FORBIDDEN),
    
    // --- Validation Errors (Lỗi kiểm tra dữ liệu đầu vào - 7xxx) ---
    INVALID_REQUEST(7001, "Invalid request", HttpStatus.BAD_REQUEST),
    MISSING_REQUIRED_FIELD(7002, "Missing required field", HttpStatus.BAD_REQUEST),
    INVALID_DATE_FORMAT(7003, "Invalid date format", HttpStatus.BAD_REQUEST),
    ;
    
    private final int code;           // Mã lỗi nội bộ (Business Code)
    private final String message;     // Thông báo lỗi mặc định
    private final HttpStatusCode statusCode; // Mã lỗi HTTP chuẩn (400, 404, 500...)
    
    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
