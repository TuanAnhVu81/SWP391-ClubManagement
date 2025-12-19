package com.swp391.clubmanagement.dto.response;

import com.swp391.clubmanagement.enums.ClubCategory;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

/**
 * ClubResponse - DTO cho response thông tin CLB
 * 
 * DTO này chứa thông tin về một CLB được trả về cho client.
 * Bao gồm thông tin cơ bản của CLB, thông tin người sáng lập, và số lượng thành viên.
 * Được sử dụng trong các endpoint GET /clubs/{id}, GET /clubs, etc.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubResponse {
    // ========== THÔNG TIN CƠ BẢN CỦA CLB ==========
    
    /** ID của CLB */
    Integer clubId;
    
    /** Tên CLB */
    String clubName;
    
    /** Danh mục CLB (HocThuat, TheThao, NgheThuat, TinhNguyen, Khac) */
    ClubCategory category;
    
    /** URL của logo CLB */
    String logo;
    
    /** Địa điểm hoạt động */
    String location;
    
    /** Mô tả chi tiết về CLB */
    String description;
    
    /** Email liên hệ */
    String email;
    
    /** Trạng thái hoạt động: true = đang hoạt động, false = đã bị vô hiệu hóa */
    Boolean isActive;
    
    /** Ngày thành lập CLB */
    LocalDate establishedDate;
    
    // ========== THÔNG TIN NGƯỜI SÁNG LẬP ==========
    
    /** ID của người sáng lập CLB */
    String founderId;
    
    /** Tên của người sáng lập */
    String founderName;
    
    /** Mã sinh viên của người sáng lập */
    String founderStudentCode;
    
    // ========== THỐNG KÊ ==========
    
    /** Tổng số thành viên chính thức (đã duyệt và đã đóng phí thành công) */
    Long totalMembers;
}
