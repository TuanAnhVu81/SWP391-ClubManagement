package com.swp391.clubmanagement.exception;

import lombok.Getter;
import lombok.Setter;

/**
 * AppException: Custom Exception class cho toàn bộ ứng dụng.
 * Sử dụng để throw các lỗi nghiệp vụ (Business Logic Errors) đã được định nghĩa trong ErrorCode.
 * 
 * Ví dụ sử dụng:
 * throw new AppException(ErrorCode.USER_NOT_FOUND);
 */
@Getter
@Setter
public class AppException extends RuntimeException {
    
    private ErrorCode errorCode;
    
    /**
     * Constructor sử dụng message mặc định từ ErrorCode
     * @param errorCode Mã lỗi từ enum
     */
    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
    
    /**
     * Constructor cho phép tùy chỉnh message lỗi
     * @param errorCode Mã lỗi từ enum
     * @param message Thông báo lỗi tùy chỉnh (ghi đè message mặc định)
     */
    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}

