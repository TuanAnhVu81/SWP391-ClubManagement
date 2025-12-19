package com.swp391.clubmanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Users Entity - Đại diện cho bảng Users trong database
 * 
 * Entity này lưu trữ thông tin về người dùng trong hệ thống quản lý CLB:
 * - Thông tin cá nhân: mã sinh viên, họ tên, email, số điện thoại, avatar
 * - Thông tin xác thực: mật khẩu (đã mã hóa), mã xác thực email, thời hạn xác thực
 * - Quan hệ: Vai trò (Role), các CLB đã thành lập, các đơn đăng ký, các đơn đã duyệt
 * 
 * Annotation @EntityListeners tự động cập nhật createdAt khi entity được tạo/cập nhật
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@EntityListeners(com.swp391.clubmanagement.configuration.EntityAuditListener.class)
@Table(name = "Users")
public class Users {
    
    // Khóa chính: UUID được tự động sinh ra
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    String userId;
    
    // Mã sinh viên: phải duy nhất và không được null
    @Column(name = "student_code", unique = true, nullable = false)
    String studentCode;
    
    // Họ và tên đầy đủ: bắt buộc phải có
    @Column(name = "full_name", nullable = false)
    String fullName;
    
    // Chuyên ngành học
    @Column(name = "major")
    String major;
    
    // Số điện thoại: tối đa 15 ký tự
    @Column(name = "phone_number", length = 15)
    String phoneNumber;
    
    // URL của ảnh đại diện
    @Column(name = "avatar_url")
    String avatarUrl;
    
    // Email: bắt buộc, dùng để đăng nhập và xác thực tài khoản
    @Column(name = "email", nullable = false)
    String email;

    // Mật khẩu đã được mã hóa: @JsonIgnore để không trả về trong JSON response
    @JsonIgnore
    @Column(name = "password")
    String password;

    // Mã xác thực email: được gửi đến email khi đăng ký/đổi mật khẩu
    @JsonIgnore
    @Column(name = "verification_code")
    String verificationCode;

    // Thời điểm mã xác thực hết hạn: sau thời điểm này mã không còn hiệu lực
    @JsonIgnore
    @Column(name = "verification_expiry")
    LocalDateTime verificationExpiry;

    // Trạng thái kích hoạt tài khoản: false = chưa xác thực email, true = đã xác thực
    @Column(name = "is_enabled")
    @Builder.Default
    boolean enabled = false;
    
    // Trạng thái hoạt động: true = tài khoản đang hoạt động, false = đã bị vô hiệu hóa
    @Column(name = "is_active")
    @Builder.Default
    Boolean isActive = true;
    
    // Thời điểm tài khoản được tạo: tự động set bởi EntityAuditListener
    @Column(name = "created_at")
    LocalDateTime createdAt;
    
    // Quan hệ Many-to-One với Roles: Mỗi user có một vai trò (Student, Admin, Leader)
    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    Roles role;
    
    // ========== CÁC QUAN HỆ (Relationships) ==========
    // @JsonIgnore để tránh circular reference khi serialize JSON và tránh lộ thông tin nhạy cảm
    
    // Danh sách các đơn xin thành lập CLB mà user này đã tạo
    @JsonIgnore
    @OneToMany(mappedBy = "creator")
    Set<ClubApplications> createdApplications;
    
    // Danh sách các đơn xin thành lập CLB mà user này đã duyệt (nếu là Admin)
    @JsonIgnore
    @OneToMany(mappedBy = "reviewer")
    Set<ClubApplications> reviewedApplications;
    
    // Danh sách các CLB mà user này là người sáng lập (Founder)
    @JsonIgnore
    @OneToMany(mappedBy = "founder")
    Set<Clubs> foundedClubs;
    
    // Danh sách các đơn đăng ký tham gia CLB của user này
    @JsonIgnore
    @OneToMany(mappedBy = "user")
    Set<Registers> registers;
    
    // Danh sách các đơn đăng ký mà user này đã duyệt (Leader/Admin)
    @JsonIgnore
    @OneToMany(mappedBy = "approver")
    Set<Registers> approvedRegisters;
}
