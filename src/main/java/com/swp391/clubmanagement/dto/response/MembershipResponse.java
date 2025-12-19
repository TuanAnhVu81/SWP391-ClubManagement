package com.swp391.clubmanagement.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * MembershipResponse - DTO cho response thông tin gói membership
 * 
 * DTO này chứa thông tin về một gói membership (gói đăng ký) của CLB.
 * Bao gồm thông tin gói và thông tin CLB mà gói này thuộc về.
 * Được sử dụng trong các endpoint GET /packages, GET /clubs/{id}/packages, etc.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MembershipResponse {
    /** ID của gói membership */
    Integer packageId;
    
    /** ID của CLB mà gói này thuộc về */
    Integer clubId;
    
    /** Tên CLB (để hiển thị, không cần query thêm) */
    String clubName;
    
    /** Tên gói membership: Ví dụ "Gói 1 tháng", "Gói học kỳ" */
    String packageName;
    
    /** Thời hạn của gói: Ví dụ "1 tháng", "3 tháng", "1 năm", "Học kỳ" */
    String term;
    
    /** Giá của gói (VND): Dùng BigDecimal để đảm bảo độ chính xác với tiền tệ */
    BigDecimal price;
    
    /** Mô tả chi tiết về gói: quyền lợi, điều kiện... */
    String description;
    
    /** Trạng thái hoạt động: true = gói đang được bán, false = đã ngừng cung cấp */
    Boolean isActive;
    
    /** Thời điểm gói được tạo */
    LocalDateTime createdAt;
}

