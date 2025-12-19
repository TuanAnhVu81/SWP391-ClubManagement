package com.swp391.clubmanagement.dto.response;

import com.swp391.clubmanagement.enums.ClubRoleType;
import com.swp391.clubmanagement.enums.JoinStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * RegisterResponse - DTO cho response thông tin đơn đăng ký tham gia CLB
 * 
 * DTO này chứa đầy đủ thông tin về một đơn đăng ký (Register), bao gồm:
 * - Thông tin user đăng ký
 * - Thông tin CLB và gói membership
 * - Trạng thái đơn và thanh toán
 * - Thông tin về thời hạn và khả năng gia hạn
 * 
 * Được sử dụng trong các endpoint GET /registers/{id}, GET /registers, etc.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegisterResponse {
    // ========== THÔNG TIN ĐƠN ĐĂNG KÝ ==========
    
    /** ID của đơn đăng ký (subscription_id) */
    Integer subscriptionId;
    
    // ========== THÔNG TIN SINH VIÊN ĐĂNG KÝ ==========
    
    /** ID của user đăng ký */
    String userId;
    
    /** Mã sinh viên */
    String studentCode;
    
    /** Tên sinh viên */
    String studentName;
    
    /** Email sinh viên */
    String studentEmail;
    
    // ========== THÔNG TIN CLB ==========
    
    /** ID của CLB */
    Integer clubId;
    
    /** Tên CLB */
    String clubName;
    
    /** Logo CLB */
    String clubLogo;
    
    // ========== THÔNG TIN GÓI MEMBERSHIP ==========
    
    /** ID của gói membership */
    Integer packageId;
    
    /** Tên gói membership */
    String packageName;
    
    /** Thời hạn gói: Ví dụ "1 tháng", "3 tháng" */
    String term;
    
    /** Giá gói (VND) */
    BigDecimal price;
    
    // ========== TRẠNG THÁI ==========
    
    /** Trạng thái đơn: ChoDuyet, DaDuyet, TuChoi, DaRoiCLB, HetHan */
    JoinStatus status;
    
    /** Lý do gia nhập CLB do user viết */
    String joinReason;
    
    /** Đã thanh toán chưa: true = đã thanh toán, false = chưa thanh toán */
    Boolean isPaid;
    
    /** Phương thức thanh toán: Ví dụ "PayOS", "Chuyển khoản" */
    String paymentMethod;
    
    /** Vai trò của user trong CLB: ThanhVien, ChuTich, PhoChuTich, ThuKy */
    ClubRoleType clubRole;
    
    // ========== THÔNG TIN NGƯỜI DUYỆT ==========
    
    /** Tên của người đã duyệt đơn (Leader/Admin) */
    String approverName;
    
    // ========== THỜI GIAN ==========
    
    /** Thời điểm đơn được tạo */
    LocalDateTime createdAt;
    
    /** Thời điểm thanh toán thành công */
    LocalDateTime paymentDate;
    
    /** Ngày bắt đầu hiệu lực membership */
    LocalDateTime startDate;
    
    /** Ngày kết thúc hiệu lực membership */
    LocalDateTime endDate;
    
    /** Ngày thực tế user tham gia CLB (sau khi được duyệt và thanh toán) */
    LocalDateTime joinDate;
    
    // ========== THÔNG TIN GIA HẠN (cho FE hiển thị nút gia hạn) ==========
    
    /** Có thể gia hạn không: true nếu status = HetHan, false nếu còn hạn hoặc không phù hợp */
    Boolean canRenew;
    
    /** Đã hết hạn chưa: true nếu endDate < now, false nếu còn hạn */
    Boolean isExpired;
}

