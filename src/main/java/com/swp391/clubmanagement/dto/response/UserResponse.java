package com.swp391.clubmanagement.dto.response;

import com.swp391.clubmanagement.enums.RoleType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

/**
 * UserResponse - DTO cho response thông tin user
 * 
 * DTO này chứa thông tin user được trả về cho client.
 * KHÔNG chứa thông tin nhạy cảm như password, verificationCode.
 * Được sử dụng trong các endpoint GET /users/{id}, GET /users/me, etc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {
    /** ID duy nhất của user (UUID) */
    String userId;
    
    /** Họ và tên đầy đủ */
    String fullName;
    
    /** Email của user */
    String email;
    
    /** Số điện thoại */
    String phoneNumber;
    
    /** Mã sinh viên */
    String studentCode;
    
    /** Chuyên ngành học */
    String major;
    
    /** Vai trò trong hệ thống (QuanTriVien, SinhVien, ChuTich) */
    RoleType role;
    
    /** Trạng thái hoạt động: true = đang hoạt động, false = đã bị vô hiệu hóa */
    boolean isActive;
    
    /** Thời điểm tài khoản được tạo */
    LocalDateTime createdAt;
    
    /** Danh sách ID các CLB mà user đang là thành viên chính thức (đã duyệt và đã thanh toán) */
    List<Integer> clubIds;
}