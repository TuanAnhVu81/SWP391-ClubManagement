package com.swp391.clubmanagement.entity;

import com.swp391.clubmanagement.enums.RoleType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Roles Entity - Đại diện cho bảng Roles trong database
 * 
 * Entity này lưu trữ các vai trò (role) trong hệ thống:
 * - Student: Sinh viên bình thường
 * - Leader: Người đứng đầu CLB (Chủ nhiệm, Phó chủ nhiệm)
 * - Admin: Quản trị viên hệ thống (có quyền duyệt đơn thành lập CLB)
 * 
 * Mỗi User có một Role, Role quyết định quyền truy cập và chức năng mà User có thể sử dụng
 * 
 * Lưu ý: Entity này KHÔNG có @EntityListeners vì không cần audit timestamps
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "Roles")
public class Roles {
    
    // Khóa chính: ID tự tăng
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    Integer roleId;
    
    // Tên vai trò: Phải duy nhất và không được null
    // Sử dụng enum RoleType để đảm bảo chỉ có các giá trị hợp lệ
    @Enumerated(EnumType.STRING)
    @Column(name = "role_name", nullable = false, unique = true)
    RoleType roleName;
}

