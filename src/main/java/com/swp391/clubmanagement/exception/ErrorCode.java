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
    SUCCESS(1000, "Thành công", HttpStatus.OK),
    
    // --- General Errors (Lỗi chung hệ thống - 1xxx) ---
    UNCATEGORIZED_EXCEPTION(9999, "Lỗi chưa được phân loại", HttpStatus.INTERNAL_SERVER_ERROR), // Lỗi chưa được định nghĩa
    INVALID_KEY(1001, "Khóa thông báo không hợp lệ", HttpStatus.BAD_REQUEST), // Key message không hợp lệ (thường dùng cho i18n)
    EMAIL_SEND_FAILED(1002, "Gửi email thất bại", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_VERIFICATION_CODE(1003, "Mã xác thực không hợp lệ", HttpStatus.BAD_REQUEST),
    VERIFICATION_LINK_EXPIRED(1004, "Liên kết xác thực đã hết hạn", HttpStatus.BAD_REQUEST),
    EMAIL_ALREADY_EXISTS(1005, "Email đã được sử dụng bởi CLB khác", HttpStatus.BAD_REQUEST),
    
    // --- User Related Errors (Lỗi liên quan đến người dùng - 2xxx) ---
    USER_EXISTED(2001, "Người dùng đã tồn tại", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(2002, "Không tìm thấy người dùng", HttpStatus.NOT_FOUND),
    USERNAME_INVALID(2003, "Tên người dùng phải có ít nhất 3 ký tự", HttpStatus.BAD_REQUEST),
    PASSWORD_INVALID(2004, "Mật khẩu phải có ít nhất 8 ký tự", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL(2005, "Định dạng email không hợp lệ", HttpStatus.BAD_REQUEST),
    EMAIL_NOT_EXIST(2006, "Email không tồn tại", HttpStatus.NOT_FOUND),
    CANNOT_DELETE_FOUNDER(2007, "Không thể xóa người dùng là người sáng lập câu lạc bộ. Vui lòng chuyển quyền sở hữu trước.", HttpStatus.BAD_REQUEST),
    USER_DEACTIVATED(2008, "Tài khoản của bạn đã bị vô hiệu hóa bởi quản trị viên. Vui lòng liên hệ hỗ trợ để được trợ giúp.", HttpStatus.FORBIDDEN),
    
    // --- Authentication & Authorization Errors (Lỗi xác thực & phân quyền - 3xxx) ---
    UNAUTHENTICATED(3001, "Chưa xác thực", HttpStatus.UNAUTHORIZED), // Chưa đăng nhập hoặc token không hợp lệ
    UNAUTHORIZED(3002, "Bạn không có quyền", HttpStatus.FORBIDDEN), // Đã đăng nhập nhưng không có quyền truy cập
    INVALID_TOKEN(3003, "Token không hợp lệ", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(3004, "Token đã hết hạn", HttpStatus.UNAUTHORIZED),
    EMAIL_NOT_VERIFIED(3005, "Email chưa được xác thực. Vui lòng kiểm tra hộp thư của bạn.", HttpStatus.UNAUTHORIZED), // Chưa xác thực email
    WRONG_PASSWORD(3006, "Mật khẩu không đúng", HttpStatus.UNAUTHORIZED),
    
    // --- Club Related Errors (Lỗi liên quan đến CLB - 4xxx) ---
    CLUB_NOT_FOUND(4001, "Không tìm thấy câu lạc bộ", HttpStatus.NOT_FOUND),
    CLUB_EXISTED(4002, "Câu lạc bộ đã tồn tại", HttpStatus.BAD_REQUEST),
    CLUB_NAME_INVALID(4003, "Tên câu lạc bộ không hợp lệ", HttpStatus.BAD_REQUEST),
    
    // --- Membership Related Errors (Lỗi thành viên/gói - 5xxx) ---
    MEMBERSHIP_NOT_FOUND(5001, "Không tìm thấy thành viên", HttpStatus.NOT_FOUND),
    ALREADY_MEMBER(5002, "Đã là thành viên của câu lạc bộ này", HttpStatus.BAD_REQUEST),
    NOT_CLUB_MEMBER(5003, "Không phải thành viên của câu lạc bộ này", HttpStatus.FORBIDDEN),
    PACKAGE_NOT_FOUND(5004, "Không tìm thấy gói", HttpStatus.NOT_FOUND),
    PACKAGE_NOT_ACTIVE(5005, "Gói không hoạt động", HttpStatus.BAD_REQUEST),
    ALREADY_REGISTERED(5006, "Đã đăng ký gói này", HttpStatus.BAD_REQUEST),
    REGISTER_NOT_FOUND(5007, "Không tìm thấy đăng ký", HttpStatus.NOT_FOUND),
    PRESIDENT_CANNOT_LEAVE(5008, "Chủ tịch câu lạc bộ không thể rời khỏi câu lạc bộ. Vui lòng chuyển quyền lãnh đạo trước.", HttpStatus.FORBIDDEN),
    NOT_ACTIVE_MEMBER(5009, "Bạn không phải là thành viên hoạt động của câu lạc bộ này", HttpStatus.BAD_REQUEST),
    MEMBERSHIP_NOT_EXPIRED(5010, "Gói membership chưa hết hạn, không thể gia hạn", HttpStatus.BAD_REQUEST),
    CANNOT_RENEW_SUBSCRIPTION(5011, "Không thể gia hạn đăng ký này. Chỉ gia hạn được khi trạng thái là HetHan", HttpStatus.BAD_REQUEST),
    ALREADY_CLUB_MEMBER(5012, "Bạn đang là thành viên của câu lạc bộ khác. Không thể tạo câu lạc bộ mới khi vẫn còn là thành viên.", HttpStatus.BAD_REQUEST),
    
    // --- Application Related Errors (Lỗi đơn đăng ký - 6xxx) ---
    APPLICATION_NOT_FOUND(6001, "Không tìm thấy đơn đăng ký", HttpStatus.NOT_FOUND),
    APPLICATION_ALREADY_SUBMITTED(6002, "Đơn đăng ký đã được gửi", HttpStatus.BAD_REQUEST),
    INVALID_APPLICATION_STATUS(6003, "Trạng thái đơn đăng ký không hợp lệ", HttpStatus.BAD_REQUEST),
    APPLICATION_ALREADY_REVIEWED(6004, "Đơn đăng ký đã được xem xét", HttpStatus.BAD_REQUEST),
    NOT_CLUB_LEADER(6005, "Bạn không phải là lãnh đạo của câu lạc bộ này", HttpStatus.FORBIDDEN),
    
    // --- Validation Errors (Lỗi kiểm tra dữ liệu đầu vào - 7xxx) ---
    INVALID_REQUEST(7001, "Yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),
    MISSING_REQUIRED_FIELD(7002, "Thiếu trường bắt buộc", HttpStatus.BAD_REQUEST),
    INVALID_DATE_FORMAT(7003, "Định dạng ngày không hợp lệ", HttpStatus.BAD_REQUEST),
    
    // --- Payment Related Errors (Lỗi thanh toán - 8xxx) ---
    PAYMENT_LINK_CREATION_FAILED(8001, "Tạo liên kết thanh toán thất bại", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_PAYMENT_SIGNATURE(8002, "Chữ ký thanh toán không hợp lệ", HttpStatus.BAD_REQUEST),
    PAYMENT_NOT_FOUND(8003, "Không tìm thấy thanh toán", HttpStatus.NOT_FOUND),
    PAYMENT_ALREADY_PROCESSED(8004, "Thanh toán đã được xử lý", HttpStatus.BAD_REQUEST),
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
