package com.swp391.clubmanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Memberships Entity - Đại diện cho bảng Memberships trong database
 * 
 * Entity này lưu trữ thông tin về các gói membership (gói đăng ký) của mỗi CLB:
 * - Thông tin gói: Tên gói, thời hạn (1 tháng, 3 tháng, 1 năm...), giá, mô tả
 * - Thuộc về CLB nào: Mỗi gói membership thuộc về một CLB cụ thể
 * - Quan hệ: Danh sách các đăng ký (Registers) sử dụng gói này
 * 
 * Ví dụ: CLB A có 3 gói: "1 tháng - 100k", "3 tháng - 250k", "1 năm - 800k"
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@EntityListeners(com.swp391.clubmanagement.configuration.EntityAuditListener.class)
@Table(name = "Memberships")
public class Memberships {
    
    // Khóa chính: ID tự tăng
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "package_id")
    Integer packageId;
    
    // Quan hệ Many-to-One với Clubs: Gói membership này thuộc về CLB nào
    @ManyToOne
    @JoinColumn(name = "club_id", nullable = false)
    Clubs club;
    
    // Tên gói membership: Ví dụ: "Gói 1 tháng", "Gói học kỳ", "Gói năm học"
    @Column(name = "package_name", nullable = false)
    String packageName;
    
    // Thời hạn của gói: Ví dụ: "1 tháng", "3 tháng", "1 năm", "Học kỳ"
    @Column(name = "term", nullable = false)
    String term;
    
    // Giá của gói: Dùng BigDecimal để đảm bảo độ chính xác với tiền tệ
    // precision = 10: tổng số chữ số, scale = 2: số chữ số sau dấu phẩy
    @Column(name = "price", precision = 10, scale = 2)
    @Builder.Default
    BigDecimal price = BigDecimal.ZERO;
    
    // Mô tả chi tiết về gói membership: quyền lợi, điều kiện...
    @Column(name = "description", columnDefinition = "TEXT")
    String description;
    
    // Trạng thái hoạt động: true = gói đang được bán, false = đã ngừng cung cấp
    @Column(name = "is_active")
    @Builder.Default
    Boolean isActive = true;
    
    // Thời điểm gói membership được tạo: tự động set bởi EntityAuditListener
    @Column(name = "created_at")
    LocalDateTime createdAt;
    
    // ========== CÁC QUAN HỆ (Relationships) ==========
    
    // Quan hệ One-to-Many với Registers: Danh sách các đăng ký sử dụng gói này
    // @JsonIgnore để tránh circular reference khi serialize JSON
    @JsonIgnore
    @OneToMany(mappedBy = "membershipPackage")
    Set<Registers> registers;
}

