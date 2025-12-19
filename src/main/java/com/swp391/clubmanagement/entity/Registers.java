package com.swp391.clubmanagement.entity;

import com.swp391.clubmanagement.enums.ClubRoleType;
import com.swp391.clubmanagement.enums.JoinStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

/**
 * Registers Entity - Đại diện cho bảng Registers trong database
 * 
 * Entity này lưu trữ thông tin về việc đăng ký tham gia CLB của một User:
 * - Thông tin đăng ký: User nào đăng ký gói membership nào
 * - Quy trình duyệt: Trạng thái đơn (Chờ duyệt, Đã duyệt, Từ chối), người duyệt, lý do
 * - Thanh toán: Trạng thái thanh toán, phương thức, thông tin PayOS
 * - Vai trò và thời hạn: Vai trò trong CLB, ngày bắt đầu/kết thúc membership
 * 
 * Constraint: Mỗi user chỉ có thể đăng ký một gói membership một lần (unique user_id + package_id)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@EntityListeners(com.swp391.clubmanagement.configuration.EntityAuditListener.class)
@Table(name = "Registers", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "package_id"}))
public class Registers {
    
    // Khóa chính: ID tự tăng
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_id")
    Integer subscriptionId;
    
    // Quan hệ Many-to-One với Users: User nào đăng ký
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    Users user;
    
    // Quan hệ Many-to-One với Memberships: Gói membership nào được đăng ký
    @ManyToOne
    @JoinColumn(name = "package_id", nullable = false)
    Memberships membershipPackage;
    
    // ========== THÔNG TIN XỬ LÝ ĐƠN ==========
    
    // Trạng thái đơn đăng ký: ChờDuyet, DaDuyet, TuChoi
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    JoinStatus status = JoinStatus.ChoDuyet;
    
    // Lý do gia nhập CLB: User viết khi đăng ký
    @Column(name = "join_reason", columnDefinition = "TEXT")
    String joinReason;
    
    // Quan hệ Many-to-One với Users: Admin/Leader nào đã duyệt đơn này
    @ManyToOne
    @JoinColumn(name = "approver_id")
    Users approver;
    
    // ========== THÔNG TIN THANH TOÁN ==========
    
    // Đã thanh toán chưa: true = đã thanh toán, false = chưa thanh toán
    @Column(name = "is_paid")
    @Builder.Default
    Boolean isPaid = false;
    
    // Ngày giờ thanh toán thành công
    @Column(name = "payment_date")
    LocalDateTime paymentDate;
    
    // Phương thức thanh toán: Ví dụ: "PayOS", "Chuyển khoản", "Tiền mặt"...
    @Column(name = "payment_method")
    String paymentMethod;
    
    // ========== THÔNG TIN PAYOS (Hệ thống thanh toán) ==========
    
    // Mã đơn hàng PayOS: Dùng để tra cứu giao dịch trên PayOS
    @Column(name = "payos_order_code")
    Long payosOrderCode;
    
    // ID của link thanh toán PayOS: Link thanh toán được tạo bởi PayOS
    @Column(name = "payos_payment_link_id")
    String payosPaymentLinkId;
    
    // Reference code từ PayOS: Mã tham chiếu của giao dịch
    @Column(name = "payos_reference")
    String payosReference;
    
    // ========== VAI TRÒ VÀ HIỆU LỰC ==========
    
    // Vai trò của user trong CLB: ThanhVien (thành viên), PhoChuNhiem, ChuNhiem...
    @Enumerated(EnumType.STRING)
    @Column(name = "club_role")
    @Builder.Default
    ClubRoleType clubRole = ClubRoleType.ThanhVien;
    
    // Ngày bắt đầu hiệu lực của membership
    @Column(name = "start_date")
    LocalDateTime startDate;
    
    // Ngày kết thúc hiệu lực của membership
    @Column(name = "end_date")
    LocalDateTime endDate;
    
    // Ngày thực tế user tham gia CLB (sau khi được duyệt và thanh toán)
    @Column(name = "join_date")
    LocalDateTime joinDate;
    
    // Thời điểm đơn đăng ký được tạo: tự động set bởi EntityAuditListener
    @Column(name = "created_at")
    LocalDateTime createdAt;
}

