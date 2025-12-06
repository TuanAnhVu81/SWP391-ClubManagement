package com.swp391.clubmanagement.controller;

import com.swp391.clubmanagement.dto.request.ClubUpdateRequest;
import com.swp391.clubmanagement.dto.response.ApiResponse;
import com.swp391.clubmanagement.dto.response.ClubMemberResponse;
import com.swp391.clubmanagement.dto.response.ClubResponse;
import com.swp391.clubmanagement.enums.ClubCategory;
import com.swp391.clubmanagement.service.ClubService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/clubs")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Tag(name = "Club Management", description = "APIs quản lý thông tin CLB")
public class ClubController {
    
    ClubService clubService;
    
    /**
     * GET /api/v1/clubs?name=&category=
     * Danh sách tất cả CLB đang hoạt động (Search theo tên, category)
     * Public API - không cần authentication
     */
    @GetMapping
    @Operation(summary = "Danh sách CLB", description = "Danh sách tất cả CLB đang hoạt động (Search theo tên, category)")
    public ApiResponse<List<ClubResponse>> getAllClubs(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) ClubCategory category) {
        
        List<ClubResponse> responses = clubService.getAllClubs(name, category);
        
        return ApiResponse.<List<ClubResponse>>builder()
                .result(responses)
                .build();
    }
    
    /**
     * GET /api/v1/clubs/{clubId}
     * Xem chi tiết thông tin 1 CLB
     * Public API - không cần authentication
     */
    @GetMapping("/{clubId}")
    @Operation(summary = "Chi tiết CLB", description = "Xem chi tiết thông tin 1 CLB")
    public ApiResponse<ClubResponse> getClubById(@PathVariable Integer clubId) {
        
        ClubResponse response = clubService.getClubById(clubId);
        
        return ApiResponse.<ClubResponse>builder()
                .result(response)
                .build();
    }
    
    /**
     * PUT /api/v1/clubs/{clubId}
     * Cập nhật thông tin CLB (Logo, mô tả, địa điểm sinh hoạt)
     * Chỉ Leader mới được phép cập nhật
     */
    @PutMapping("/{clubId}")
    @PreAuthorize("hasAuthority('SCOPE_SinhVien')")
    @Operation(summary = "Cập nhật thông tin CLB", description = "Cập nhật thông tin CLB (Logo, mô tả, địa điểm sinh hoạt)")
    public ApiResponse<ClubResponse> updateClub(
            @PathVariable Integer clubId,
            @Valid @RequestBody ClubUpdateRequest request) {
        
        ClubResponse response = clubService.updateClub(clubId, request);
        
        return ApiResponse.<ClubResponse>builder()
                .result(response)
                .build();
    }
    
    /**
     * GET /api/v1/clubs/{clubId}/members
     * Xem danh sách thành viên của CLB đó
     * Public API - không cần authentication
     */
    @GetMapping("/{clubId}/members")
    @Operation(summary = "Danh sách thành viên CLB", description = "Xem danh sách thành viên của CLB đó")
    public ApiResponse<List<ClubMemberResponse>> getClubMembers(@PathVariable Integer clubId) {
        
        List<ClubMemberResponse> responses = clubService.getClubMembers(clubId);
        
        return ApiResponse.<List<ClubMemberResponse>>builder()
                .result(responses)
                .build();
    }
}
