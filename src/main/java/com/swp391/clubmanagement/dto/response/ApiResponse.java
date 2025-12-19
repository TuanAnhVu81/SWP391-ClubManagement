package com.swp391.clubmanagement.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ApiResponse<T> - DTO chuẩn cho tất cả các response từ API
 * 
 * Generic class này được sử dụng làm wrapper cho tất cả các response của API,
 * cung cấp cấu trúc thống nhất: code (mã lỗi/thành công), message (thông điệp), result (dữ liệu trả về).
 * 
 * @param <T> Kiểu dữ liệu của kết quả trả về (có thể là UserResponse, ClubResponse, List<...>, etc.)
 * 
 * Ví dụ:
 * - code = 1000: Thành công
 * - code != 1000: Có lỗi xảy ra
 * - result: Dữ liệu thực tế (entity, list, object...)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {
    
    // Mã phản hồi: 1000 = thành công, các mã khác = lỗi
    @Builder.Default
    private int code = 1000;
    
    // Thông điệp mô tả kết quả hoặc lỗi
    private String message;
    
    // Dữ liệu trả về: có thể là bất kỳ kiểu nào (User, Club, List, etc.)
    private T result;
}
