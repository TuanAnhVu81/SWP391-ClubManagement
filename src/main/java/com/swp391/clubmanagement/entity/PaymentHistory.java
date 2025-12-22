package com.swp391.clubmanagement.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PaymentHistory Entity - Đại diện cho bảng PaymentHistory trong database
 * 
 * Entity này lưu trữ lịch sử các giao dịch thanh toán đã hoàn thành:
 * - Thông tin giao dịch: User nào thanh toán, CLB nào, gói nào, số tiền
 * - Thông tin PayOS: Order code, reference code để tra cứu
 * - Thời gian: Ngày giờ thanh toán thành công
 * 
 * Mục đích:
 * - Xem lịch sử giao dịch của user/CLB
 * - Tính doanh thu theo thời gian, CLB, gói membership
 * - Thống kê và báo cáo
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@EntityListeners(com.swp391.clubmanagement.configuration.EntityAuditListener.class)
@Table(name = "PaymentHistory")
public class PaymentHistory {
    
    // Khóa chính: ID tự tăng
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    Integer paymentId;
    
    // Quan hệ Many-to-One với Registers: Đăng ký nào được thanh toán
    @ManyToOne
    @JoinColumn(name = "subscription_id", nullable = false)
    Registers register;
    
    // Quan hệ Many-to-One với Users: User nào thanh toán
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    Users user;
    
    // Quan hệ Many-to-One với Clubs: CLB nào nhận thanh toán
    @ManyToOne
    @JoinColumn(name = "club_id", nullable = false)
    Clubs club;
    
    // Quan hệ Many-to-One với Memberships: Gói membership nào được thanh toán
    @ManyToOne
    @JoinColumn(name = "package_id", nullable = false)
    Memberships membershipPackage;
    
    // Số tiền thanh toán: Dùng BigDecimal để đảm bảo độ chính xác
    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    BigDecimal amount;
    
    // Phương thức thanh toán: Ví dụ: "PayOS", "Chuyển khoản", "Tiền mặt"
    @Column(name = "payment_method", nullable = false)
    String paymentMethod;
    
    // Mã đơn hàng PayOS: Dùng để tra cứu giao dịch trên PayOS
    @Column(name = "payos_order_code")
    Long payosOrderCode;
    
    // Reference code từ PayOS: Mã tham chiếu của giao dịch
    @Column(name = "payos_reference")
    String payosReference;
    
    // Ngày giờ thanh toán thành công
    @Column(name = "payment_date", nullable = false)
    LocalDateTime paymentDate;
    
    // Thời điểm record được tạo: tự động set bởi EntityAuditListener
    @Column(name = "created_at")
    LocalDateTime createdAt;
}

