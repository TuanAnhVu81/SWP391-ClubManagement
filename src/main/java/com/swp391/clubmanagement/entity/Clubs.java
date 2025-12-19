package com.swp391.clubmanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.swp391.clubmanagement.enums.ClubCategory;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.util.Set;

/**
 * Clubs Entity - Đại diện cho bảng Clubs trong database
 * 
 * Entity này lưu trữ thông tin về các Câu lạc bộ (CLB) trong hệ thống:
 * - Thông tin cơ bản: tên CLB, danh mục, logo, địa điểm, mô tả, email
 * - Người sáng lập: User đã tạo và được duyệt thành lập CLB này
 * - Quan hệ: Đơn đăng ký thành lập (ClubApplications), các gói membership
 * 
 * CLB được tạo ra sau khi đơn xin thành lập (ClubApplications) được Admin duyệt
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@EntityListeners(com.swp391.clubmanagement.configuration.EntityAuditListener.class)
@Table(name = "Clubs")
public class Clubs {
    
    // Khóa chính: ID tự tăng
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "club_id")
    Integer clubId;
    
    // Tên của CLB: bắt buộc phải có
    @Column(name = "club_name", nullable = false)
    String clubName;
    
    // Danh mục của CLB: Ví dụ: Học thuật, Thể thao, Nghệ thuật, Tình nguyện...
    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    ClubCategory category;
    
    // URL của logo CLB
    @Column(name = "logo")
    String logo;
    
    // Địa điểm hoạt động của CLB (phòng, tòa nhà, khu vực...)
    @Column(name = "location")
    String location;
    
    // Mô tả chi tiết về CLB: dùng TEXT để lưu nội dung dài
    @Column(name = "description", columnDefinition = "TEXT")
    String description;
    
    // Email liên hệ của CLB
    @Column(name = "email")
    String email;
    
    // Quan hệ Many-to-One với Users: User nào là người sáng lập CLB này
    // @JsonIgnore để không trả về thông tin founder trong JSON (tránh circular reference)
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "founder_id")
    Users founder;
    
    // Trạng thái hoạt động: true = CLB đang hoạt động, false = đã bị vô hiệu hóa
    @Column(name = "is_active")
    @Builder.Default
    Boolean isActive = true;
    
    // Ngày thành lập CLB
    @Column(name = "established_date")
    LocalDate establishedDate;
    
    // ========== CÁC QUAN HỆ (Relationships) ==========
    // @JsonIgnore để tránh circular reference khi serialize JSON
    
    // Quan hệ One-to-One với ClubApplications: Đơn đăng ký thành lập CLB này
    // Mỗi CLB được tạo từ một đơn đăng ký duy nhất
    @JsonIgnore
    @OneToOne(mappedBy = "club")
    ClubApplications application;
    
    // Quan hệ One-to-Many với Memberships: Danh sách các gói membership của CLB này
    // Mỗi CLB có nhiều gói membership khác nhau (1 tháng, 3 tháng, 1 năm...)
    @JsonIgnore
    @OneToMany(mappedBy = "club")
    Set<Memberships> memberships;
}

