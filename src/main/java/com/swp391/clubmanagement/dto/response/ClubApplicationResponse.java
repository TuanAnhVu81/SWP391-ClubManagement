package com.swp391.clubmanagement.dto.response;

import com.swp391.clubmanagement.enums.ClubCategory;
import com.swp391.clubmanagement.enums.RequestStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

/**
 * ClubApplicationResponse - DTO cho response thông tin đơn xin thành lập CLB
 * 
 * DTO này chứa thông tin về một đơn xin thành lập CLB, bao gồm:
 * - Thông tin đơn và trạng thái duyệt
 * - Thông tin người tạo đơn và người duyệt (nếu có)
 * - ID của CLB được tạo (nếu đơn đã được duyệt)
 * 
 * Được sử dụng trong các endpoint GET /club-applications/{id}, GET /club-applications, etc.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubApplicationResponse {
    // ========== THÔNG TIN ĐƠN ==========
    
    /** ID của đơn xin thành lập */
    Integer requestId;
    
    /** Tên CLB đề xuất */
    String proposedName;
    
    /** Danh mục CLB */
    ClubCategory category;
    
    /** Mục đích thành lập CLB */
    String purpose;
    
    /** Trạng thái đơn: DangCho, ChapThuan, TuChoi */
    RequestStatus status;
    
    /** Ghi chú từ Admin (nếu từ chối hoặc yêu cầu chỉnh sửa) */
    String adminNote;
    
    // ========== THÔNG TIN NGƯỜI TẠO ĐƠN ==========
    
    /** ID của user tạo đơn */
    String creatorId;
    
    /** Tên của user tạo đơn */
    String creatorName;
    
    /** Mã sinh viên của user tạo đơn */
    String creatorStudentCode;
    
    // ========== THÔNG TIN NGƯỜI DUYỆT (nếu có) ==========
    
    /** ID của Admin đã duyệt/từ chối đơn */
    String reviewerId;
    
    /** Tên của Admin đã duyệt/từ chối đơn */
    String reviewerName;
    
    // ========== THÔNG TIN CLB ==========
    
    /** ID của CLB được tạo (chỉ có khi status = ChapThuan, null nếu chưa duyệt hoặc bị từ chối) */
    Integer clubId;
    
    // ========== THỜI GIAN ==========
    
    /** Thời điểm đơn được tạo */
    LocalDateTime createdAt;
    
    /** Thời điểm đơn được cập nhật lần cuối */
    LocalDateTime updatedAt;
}
