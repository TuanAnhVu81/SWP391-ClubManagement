package com.swp391.clubmanagement.controller;

import com.swp391.clubmanagement.dto.response.AdminDashboardResponse;
import com.swp391.clubmanagement.dto.response.ApiResponse;
import com.swp391.clubmanagement.dto.response.ClubResponse;
import com.swp391.clubmanagement.dto.response.ClubStatistic;
import com.swp391.clubmanagement.service.AdminDashboardService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AdminDashboardController: API thống kê dành cho Admin
 */
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasRole('QuanTriVien')")
public class AdminDashboardController {
    AdminDashboardService adminDashboardService;

    /**
     * API Lấy tất cả dữ liệu Dashboard
     * Endpoint: GET /admin/dashboard
     */
    @GetMapping
    ApiResponse<AdminDashboardResponse> getDashboard() {
        return ApiResponse.<AdminDashboardResponse>builder()
                .result(adminDashboardService.getDashboardData())
                .build();
    }

    /**
     * API Lấy tổng số CLB
     * Endpoint: GET /admin/dashboard/total-clubs
     */
    @GetMapping("/total-clubs")
    ApiResponse<Long> getTotalClubs() {
        return ApiResponse.<Long>builder()
                .result(adminDashboardService.getTotalClubs())
                .build();
    }

    /**
     * API Lấy tổng số thành viên (memberships)
     * Endpoint: GET /admin/dashboard/total-members
     */
    @GetMapping("/total-members")
    ApiResponse<Long> getTotalMembers() {
        return ApiResponse.<Long>builder()
                .result(adminDashboardService.getTotalMembers())
                .build();
    }

    /**
     * API Lấy tổng số sinh viên duy nhất tham gia
     * Endpoint: GET /admin/dashboard/total-students
     */
    @GetMapping("/total-students")
    ApiResponse<Long> getTotalStudents() {
        return ApiResponse.<Long>builder()
                .result(adminDashboardService.getTotalStudents())
                .build();
    }

    /**
     * API Thống kê CLB theo danh mục
     * Endpoint: GET /admin/dashboard/clubs-by-category
     */
    @GetMapping("/clubs-by-category")
    ApiResponse<Map<String, Long>> getClubsByCategory() {
        return ApiResponse.<Map<String, Long>>builder()
                .result(adminDashboardService.getClubsByCategory())
                .build();
    }

    /**
     * API Thống kê thành viên theo vai trò
     * Endpoint: GET /admin/dashboard/members-by-role
     */
    @GetMapping("/members-by-role")
    ApiResponse<Map<String, Long>> getMembersByRole() {
        return ApiResponse.<Map<String, Long>>builder()
                .result(adminDashboardService.getMembersByRole())
                .build();
    }

    /**
     * API Top 5 CLB có nhiều thành viên nhất
     * Endpoint: GET /admin/dashboard/top-clubs
     */
    @GetMapping("/top-clubs")
    ApiResponse<List<ClubStatistic>> getTop5ClubsByMembers() {
        return ApiResponse.<List<ClubStatistic>>builder()
                .result(adminDashboardService.getTop5ClubsByMembers())
                .build();
    }

    /**
     * API CLB mới trong tháng
     * Endpoint: GET /admin/dashboard/new-clubs
     */
    @GetMapping("/new-clubs")
    ApiResponse<List<ClubResponse>> getNewClubsThisMonth() {
        return ApiResponse.<List<ClubResponse>>builder()
                .result(adminDashboardService.getNewClubsThisMonth())
                .build();
    }
}

