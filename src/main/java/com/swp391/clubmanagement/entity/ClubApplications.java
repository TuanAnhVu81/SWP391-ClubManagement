package com.swp391.clubmanagement.entity;

import com.swp391.clubmanagement.enums.ClubCategory;
import com.swp391.clubmanagement.enums.RequestStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ClubApplications Entity - Đại diện cho bảng ClubApplications trong database
 * 
 * Entity này lưu trữ thông tin về đơn xin thành lập CLB:
 * - Thông tin đơn: User nào tạo đơn, thông tin CLB dự kiến (tên, danh mục, mô tả...)
 * - Quy trình duyệt: Trạng thái (Đang chờ, Đã duyệt, Từ chối), Admin duyệt, ghi chú
 * - Quan hệ với Clubs: Sau khi duyệt, một CLB mới được tạo và liên kết với đơn này
 * 
 * Quy trình: User tạo đơn → Admin xem và duyệt/từ chối → Nếu duyệt thì tạo CLB mới
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@EntityListeners(com.swp391.clubmanagement.configuration.EntityAuditListener.class)
@Table(name = "ClubApplications")
public class ClubApplications {
    
    // Khóa chính: ID tự tăng
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    Integer requestId;
    
    // Quan hệ Many-to-One với Users: User nào tạo đơn xin thành lập CLB
    @ManyToOne
    @JoinColumn(name = "creator_id", nullable = false)
    Users creator;
    
    // Quan hệ One-to-One với Clubs: CLB được tạo ra sau khi đơn được duyệt
    // Ban đầu null, sau khi Admin duyệt thì CLB mới được tạo và liên kết
    @OneToOne
    @JoinColumn(name = "club_id")
    Clubs club;
    
    // ========== THÔNG TIN CLB DỰ KIẾN ==========
    
    // Tên CLB mà người tạo đơn đề xuất: bắt buộc phải có
    @Column(name = "proposed_name", nullable = false)
    String proposedName;
    
    // Danh mục CLB: Ví dụ: Học thuật, Thể thao, Nghệ thuật, Tình nguyện...
    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    ClubCategory category;
    
    // Mục đích thành lập CLB: Lý do, mục tiêu của CLB
    @Column(name = "purpose", columnDefinition = "TEXT")
    String purpose;
    
    // Mô tả chi tiết về CLB: Hoạt động, hướng phát triển, kế hoạch...
    @Column(name = "description", columnDefinition = "TEXT")
    String description;
    
    // Địa điểm hoạt động dự kiến
    @Column(name = "location")
    String location;
    
    // Email liên hệ dự kiến của CLB
    @Column(name = "email")
    String email;
    
    // Phí membership mặc định: Giá gói membership cơ bản mà CLB sẽ cung cấp
    @Column(name = "default_membership_fee", precision = 10, scale = 2)
    BigDecimal defaultMembershipFee;
    
    // ========== TRẠNG THÁI DUYỆT CỦA ADMIN ==========
    
    // Trạng thái đơn: DangCho (đang chờ), DaDuyet (đã duyệt), TuChoi (từ chối)
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    RequestStatus status = RequestStatus.DangCho;
    
    // Ghi chú từ Admin: Lý do từ chối hoặc yêu cầu chỉnh sửa
    @Column(name = "admin_note", columnDefinition = "TEXT")
    String adminNote;
    
    // Quan hệ Many-to-One với Users: Admin nào đã duyệt/từ chối đơn này
    @ManyToOne
    @JoinColumn(name = "reviewer_id")
    Users reviewer;
    
    // Thời điểm đơn được tạo: tự động set bởi EntityAuditListener
    @Column(name = "created_at")
    LocalDateTime createdAt;
    
    // Thời điểm đơn được cập nhật lần cuối: tự động set bởi EntityAuditListener
    @Column(name = "updated_at")
    LocalDateTime updatedAt;
}

