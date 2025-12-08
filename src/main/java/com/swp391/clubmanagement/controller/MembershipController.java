package com.swp391.clubmanagement.controller;

import com.swp391.clubmanagement.dto.request.MembershipCreateRequest;
import com.swp391.clubmanagement.dto.request.MembershipUpdateRequest;
import com.swp391.clubmanagement.dto.response.ApiResponse;
import com.swp391.clubmanagement.dto.response.MembershipResponse;
import com.swp391.clubmanagement.service.MembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MembershipController: API quản lý các gói thành viên của CLB
 */
@RestController
@RequestMapping("/packages")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Membership Package Management", description = "APIs quản lý gói thành viên CLB")
public class MembershipController {
    MembershipService membershipService;

    /**
     * GET /api/packages/club/{clubId}
     * Xem danh sách các gói thành viên của 1 CLB (Public)
     */
    @GetMapping("/club/{clubId}")
    @Operation(summary = "Danh sách gói thành viên", description = "Xem danh sách các gói thành viên của 1 CLB")
    public ApiResponse<List<MembershipResponse>> getPackagesByClub(@PathVariable Integer clubId) {
        return ApiResponse.<List<MembershipResponse>>builder()
                .result(membershipService.getPackagesByClub(clubId))
                .build();
    }

    /**
     * GET /api/packages/{packageId}
     * Xem chi tiết 1 gói (Public)
     */
    @GetMapping("/{packageId}")
    @Operation(summary = "Chi tiết gói thành viên", description = "Xem chi tiết 1 gói thành viên")
    public ApiResponse<MembershipResponse> getPackageById(@PathVariable Integer packageId) {
        return ApiResponse.<MembershipResponse>builder()
                .result(membershipService.getPackageById(packageId))
                .build();
    }
    
    /**
     * POST /api/clubs/{clubId}/packages
     * Tạo gói thành viên mới (insert vào bảng Memberships) - Leader only
     */
    @PostMapping("/club/{clubId}/create")
    @PreAuthorize("hasAuthority('SCOPE_SinhVien')")
    @Operation(summary = "Tạo gói thành viên mới", description = "Tạo gói thành viên mới (insert vào bảng Memberships)")
    public ApiResponse<MembershipResponse> createPackage(
            @PathVariable Integer clubId,
            @Valid @RequestBody MembershipCreateRequest request) {
        
        MembershipResponse response = membershipService.createPackage(clubId, request);
        
        return ApiResponse.<MembershipResponse>builder()
                .result(response)
                .build();
    }
    
    /**
     * PUT /api/packages/{packageId}
     * Sửa thông tin gói (Tên, giá, mô tả) - Leader only
     */
    @PutMapping("/{packageId}")
    @PreAuthorize("hasAuthority('SCOPE_SinhVien')")
    @Operation(summary = "Cập nhật gói thành viên", description = "Sửa thông tin gói (Tên, giá, mô tả)")
    public ApiResponse<MembershipResponse> updatePackage(
            @PathVariable Integer packageId,
            @Valid @RequestBody MembershipUpdateRequest request) {
        
        MembershipResponse response = membershipService.updatePackage(packageId, request);
        
        return ApiResponse.<MembershipResponse>builder()
                .result(response)
                .build();
    }
    
    /**
     * DELETE /api/packages/{packageId}
     * Đóng gói đăng ký (Soft delete hoặc set is_active=false) - Leader only
     */
    @DeleteMapping("/{packageId}")
    @PreAuthorize("hasAuthority('SCOPE_SinhVien')")
    @Operation(summary = "Đóng gói đăng ký", description = "Đóng gói đăng ký (Soft delete hoặc set is_active=false)")
    public ApiResponse<Void> deletePackage(@PathVariable Integer packageId) {
        membershipService.deletePackage(packageId);
        
        return ApiResponse.<Void>builder()
                .message("Package đã được đóng thành công")
                .build();
    }
}

