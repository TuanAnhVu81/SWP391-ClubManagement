package com.swp391.clubmanagement.entity;

import com.swp391.clubmanagement.enums.ClubRoleType;
import com.swp391.clubmanagement.enums.JoinStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

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
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_id")
    Integer subscriptionId;
    
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    Users user;
    
    @ManyToOne
    @JoinColumn(name = "package_id", nullable = false)
    Memberships membershipPackage;
    
    // Thông tin xử lý đơn
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    JoinStatus status = JoinStatus.ChoDuyet;
    
    @Column(name = "join_reason", columnDefinition = "TEXT")
    String joinReason; // Lý do gia nhập CLB
    
    @ManyToOne
    @JoinColumn(name = "approver_id")
    Users approver;
    
    // Thông tin thanh toán (Transaction)
    @Column(name = "is_paid")
    @Builder.Default
    Boolean isPaid = false;
    
    @Column(name = "payment_date")
    LocalDateTime paymentDate;
    
    @Column(name = "payment_method")
    String paymentMethod;
    
    // Thông tin PayOS
    @Column(name = "payos_order_code")
    Long payosOrderCode;
    
    @Column(name = "payos_payment_link_id")
    String payosPaymentLinkId;
    
    @Column(name = "payos_reference")
    String payosReference;
    
    // Vai trò & Hiệu lực
    @Enumerated(EnumType.STRING)
    @Column(name = "club_role")
    @Builder.Default
    ClubRoleType clubRole = ClubRoleType.ThanhVien;
    
    @Column(name = "start_date")
    LocalDateTime startDate;
    
    @Column(name = "end_date")
    LocalDateTime endDate;
    
    @Column(name = "join_date")
    LocalDateTime joinDate;
    
    @Column(name = "created_at")
    LocalDateTime createdAt;
}

