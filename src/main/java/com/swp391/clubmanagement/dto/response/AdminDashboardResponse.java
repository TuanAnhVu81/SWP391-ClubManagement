package com.swp391.clubmanagement.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminDashboardResponse {
    // Tổng quan
    Long totalClubs;
    Long totalMembers;
    Long totalStudents;
    
    // CLB theo danh mục
    Map<String, Long> clubsByCategory;
    
    // Thành viên theo vai trò trong CLB
    Map<String, Long> membersByRole;
    
    // Top 5 CLB có nhiều thành viên nhất
    List<ClubStatistic> top5ClubsByMembers;
    
    // CLB mới trong tháng
    List<ClubResponse> newClubsThisMonth;
}

