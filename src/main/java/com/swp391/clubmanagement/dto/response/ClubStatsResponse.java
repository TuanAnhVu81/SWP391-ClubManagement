package com.swp391.clubmanagement.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubStatsResponse {
    
    // Thông tin CLB
    Integer clubId;
    String clubName;
    
    // Thống kê thành viên
    Long totalMembers; // Tổng số thành viên (đã duyệt + đã đóng phí)
    Long pendingRegistrations; // Số đơn đang chờ duyệt
    Long rejectedRegistrations; // Số đơn bị từ chối
    
    // Thống kê vai trò
    Long chuTichCount; // Số Chủ tịch
    Long phoChuTichCount; // Số Phó chủ tịch
    Long thuKyCount; // Số Thư ký
    Long thanhVienCount; // Số Thành viên thường
    
    // Thống kê tài chính
    BigDecimal totalRevenue; // Tổng doanh thu từ phí thành viên
    Long paidCount; // Số người đã đóng phí
    Long unpaidCount; // Số người chưa đóng phí
    
    // Danh sách thành viên chưa đóng phí
    List<UnpaidMemberInfo> unpaidMembers;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnpaidMemberInfo {
        Integer subscriptionId;
        String studentCode;
        String fullName;
        String packageName;
        BigDecimal packagePrice;
        String joinDate;
    }
}
