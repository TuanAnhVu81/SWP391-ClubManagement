package com.swp391.clubmanagement.controller;

import com.swp391.clubmanagement.dto.request.ClubApplicationRequest;
import com.swp391.clubmanagement.dto.request.ReviewApplicationRequest;
import com.swp391.clubmanagement.dto.response.ApiResponse;
import com.swp391.clubmanagement.dto.response.ClubApplicationResponse;
import com.swp391.clubmanagement.enums.RequestStatus;
import com.swp391.clubmanagement.service.ClubApplicationService;
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
@RequestMapping("/club-requests")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Tag(name = "Club Request Management", description = "APIs quản lý đơn yêu cầu mở CLB")
public class ClubApplicationController {
    
    ClubApplicationService clubApplicationService;
    
    /**
     * POST /api/club-requests
     * Sinh viên gửi đơn yêu cầu thành lập CLB mới
     */
    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_SinhVien')")
    @Operation(summary = "Gửi đơn yêu cầu thành lập CLB mới", description = "Sinh viên gửi đơn yêu cầu thành lập CLB mới (Gửi vào bảng ClubApplications)")
    public ApiResponse<ClubApplicationResponse> createClubApplication(
            @Valid @RequestBody ClubApplicationRequest request) {
        
        ClubApplicationResponse response = clubApplicationService.createClubApplication(request);
        
        return ApiResponse.<ClubApplicationResponse>builder()
                .result(response)
                .build();
    }
    
    /**
     * GET /api/club-requests?status=DangCho
     * Admin xem danh sách các đơn yêu cầu mở CLB (Filter: DangCho/ChapThuan/TuChoi)
     */
    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_QuanTriVien')")
    @Operation(summary = "Xem danh sách đơn yêu cầu mở CLB", description = "Admin xem danh sách các đơn yêu cầu mở CLB (Filter: Pending/Approved)")
    public ApiResponse<List<ClubApplicationResponse>> getAllApplications(
            @RequestParam(required = false) RequestStatus status) {
        
        List<ClubApplicationResponse> responses = clubApplicationService.getAllApplications(status);
        
        return ApiResponse.<List<ClubApplicationResponse>>builder()
                .result(responses)
                .build();
    }
    
    /**
     * GET /api/club-requests/my-requests
     * Sinh viên xem lịch sử các đơn mình đã gửi
     */
    @GetMapping("/my-requests")
    @PreAuthorize("hasAuthority('SCOPE_SinhVien')")
    @Operation(summary = "Xem lịch sử đơn đã gửi", description = "Xem lịch sử các đơn mình đã gửi")
    public ApiResponse<List<ClubApplicationResponse>> getMyApplications() {
        
        List<ClubApplicationResponse> responses = clubApplicationService.getMyApplications();
        
        return ApiResponse.<List<ClubApplicationResponse>>builder()
                .result(responses)
                .build();
    }
    
    /**
     * PUT /api/club-requests/{requestId}/review
     * Admin duyệt (ChapThuan) hoặc từ chối đơn
     * Khi duyệt, hệ thống tự động tạo insert dữ liệu vào bảng Clubs
     */
    @PutMapping("/{requestId}/review")
    @PreAuthorize("hasAuthority('SCOPE_QuanTriVien')")
    @Operation(summary = "Duyệt hoặc từ chối đơn", 
               description = "Duyệt (ChapThuan) hoặc Từ chối đơn. Lưu ý: Khi duyệt, hệ thống tự động tạo insert dữ liệu vào bảng Clubs")
    public ApiResponse<ClubApplicationResponse> reviewApplication(
            @PathVariable Integer requestId,
            @Valid @RequestBody ReviewApplicationRequest request) {
        
        ClubApplicationResponse response = clubApplicationService.reviewApplication(requestId, request);
        
        return ApiResponse.<ClubApplicationResponse>builder()
                .result(response)
                .build();
    }
}
