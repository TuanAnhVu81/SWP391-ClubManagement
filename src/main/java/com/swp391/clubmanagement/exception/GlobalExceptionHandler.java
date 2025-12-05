package com.swp391.clubmanagement.exception;

import com.swp391.clubmanagement.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * GlobalExceptionHandler: Class quản lý Exception tập trung cho toàn bộ ứng dụng.
 * - Bắt các lỗi xảy ra trong Controller/Service
 * - Chuẩn hóa format trả về client dưới dạng ApiResponse
 * - Log lỗi để debug
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    /**
     * Xử lý các lỗi nghiệp vụ (AppException)
     * Ví dụ: User not found, Invalid password...
     */
    @ExceptionHandler(value = AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        
        // Tạo response chuẩn với code và message từ ErrorCode
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
        
        // Trả về ResponseEntity với HTTP Status Code tương ứng (400, 404, etc.)
        return ResponseEntity
                .status(errorCode.getStatusCode())
                .body(apiResponse);
    }
    
    /**
     * Xử lý lỗi Validation từ annotation @Valid (MethodArgumentNotValidException)
     * Ví dụ: @NotNull, @Size, @Email...
     * Trả về danh sách các field bị lỗi.
     */
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException exception) {
        
        // Map chứa tên trường lỗi và thông báo lỗi
        Map<String, String> errors = new HashMap<>();
        exception.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        ApiResponse<Map<String, String>> apiResponse = ApiResponse.<Map<String, String>>builder()
                .code(ErrorCode.INVALID_REQUEST.getCode())
                .message(ErrorCode.INVALID_REQUEST.getMessage())
                .result(errors) // Trả về chi tiết lỗi validation
                .build();
        
        return ResponseEntity
                .badRequest()
                .body(apiResponse);
    }
    
    /**
     * Xử lý tất cả các lỗi không xác định (Uncategorized Exceptions)
     * Fallback cho các lỗi chưa được handle cụ thể (Lỗi hệ thống, database...)
     */
    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUncategorizedException(Exception exception) {
        log.error("Uncategorized exception: ", exception); // Log stack trace để debug
        
        ErrorCode errorCode = ErrorCode.UNCATEGORIZED_EXCEPTION;
        
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage()) // "Uncategorized error"
                .build();
        
        return ResponseEntity
                .status(errorCode.getStatusCode()) // Thường là 500 Internal Server Error
                .body(apiResponse);
    }
    
    /**
     * Xử lý lỗi IllegalArgumentException (Tham số không hợp lệ)
     */
    @ExceptionHandler(value = IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(
            IllegalArgumentException exception) {
        
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                .code(ErrorCode.INVALID_REQUEST.getCode())
                .message(exception.getMessage())
                .build();
        
        return ResponseEntity
                .badRequest()
                .body(apiResponse);
    }
    
    /**
     * Xử lý lỗi NullPointerException (Lỗi null pointer)
     * Nên log kỹ để fix bug
     */
    @ExceptionHandler(value = NullPointerException.class)
    public ResponseEntity<ApiResponse<Void>> handleNullPointerException(NullPointerException exception) {
        log.error("NullPointerException: ", exception);
        
        ErrorCode errorCode = ErrorCode.UNCATEGORIZED_EXCEPTION;
        
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                .code(errorCode.getCode())
                .message("Null pointer exception occurred")
                .build();
        
        return ResponseEntity
                .status(errorCode.getStatusCode())
                .body(apiResponse);
    }
}

