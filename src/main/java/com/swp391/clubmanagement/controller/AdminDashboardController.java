package com.swp391.clubmanagement.controller;

import com.swp391.clubmanagement.dto.response.AdminDashboardResponse;
import com.swp391.clubmanagement.dto.response.ApiResponse;
import com.swp391.clubmanagement.dto.response.ClubResponse;
import com.swp391.clubmanagement.dto.response.ClubStatistic;
import com.swp391.clubmanagement.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Admin Dashboard", description = "APIs thống kê và dashboard dành cho Quản trị viên")
public class AdminDashboardController {
    AdminDashboardService adminDashboardService;

    /**
     * API Lấy tất cả dữ liệu Dashboard
     * Endpoint: GET /admin/dashboard
     */
    @GetMapping
    @Operation(summary = "Lấy tất cả dữ liệu Dashboard", 
               description = "Lấy tất cả dữ liệu thống kê tổng hợp cho Admin Dashboard bao gồm: tổng số CLB, tổng số thành viên, tổng số sinh viên, thống kê theo danh mục, thống kê theo vai trò, top CLB, CLB mới trong tháng.")
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
    @Operation(summary = "Tổng số CLB", 
               description = "Lấy tổng số CLB đang hoạt động trong hệ thống.")
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
    @Operation(summary = "Tổng số thành viên", 
               description = "Lấy tổng số thành viên (memberships) đã được duyệt và đã đóng phí trong tất cả các CLB.")
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
    @Operation(summary = "Tổng số sinh viên duy nhất", 
               description = "Lấy tổng số sinh viên duy nhất đã tham gia ít nhất một CLB (đã được duyệt và đã đóng phí).")
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
    @Operation(summary = "Thống kê CLB theo danh mục", 
               description = "Lấy thống kê số lượng CLB theo từng danh mục (VănHoa, TheThao, HocThuat, KyThuat, XaHoi, Khac).")
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
    @Operation(summary = "Thống kê thành viên theo vai trò", 
               description = "Lấy thống kê số lượng thành viên theo từng vai trò trong CLB (ChuTich, PhoChuTich, ThuKy, ThanhVien).")
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
    @Operation(summary = "Top 5 CLB có nhiều thành viên nhất", 
               description = "Lấy danh sách top 5 CLB có số lượng thành viên nhiều nhất, bao gồm thông tin CLB (ID, tên, logo, danh mục) và số lượng thành viên.")
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
    @Operation(summary = "CLB mới trong tháng", 
               description = "Lấy danh sách các CLB mới được thành lập trong tháng hiện tại, sắp xếp theo ngày thành lập mới nhất.")
    ApiResponse<List<ClubResponse>> getNewClubsThisMonth() {
        return ApiResponse.<List<ClubResponse>>builder()
                .result(adminDashboardService.getNewClubsThisMonth())
                .build();
    }
}

