package com.swp391.clubmanagement.controller;

import com.swp391.clubmanagement.dto.request.ApproveRegisterRequest;
import com.swp391.clubmanagement.dto.request.ChangeRoleRequest;
import com.swp391.clubmanagement.dto.request.ConfirmPaymentRequest;
import com.swp391.clubmanagement.dto.response.ApiResponse;
import com.swp391.clubmanagement.dto.response.RegisterResponse;
import com.swp391.clubmanagement.enums.JoinStatus;
import com.swp391.clubmanagement.service.LeaderRegisterService;
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
 * LeaderRegisterController: API cho Chủ nhiệm CLB xử lý đơn đăng ký
 * 
 * Luồng: Xem đơn -> Duyệt/Từ chối -> Xác nhận thanh toán -> Thành viên chính thức
 */
@RestController
@RequestMapping("/registrations")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Leader Registration Management", description = "APIs cho Leader quản lý đơn đăng ký và thành viên")
public class LeaderRegisterController {
    LeaderRegisterService leaderRegisterService;

    /**
     * GET /api/registrations/club/{clubId}
     * Xem danh sách tất cả đơn đăng ký vào CLB mình
     */
    @GetMapping("/club/{clubId}")
    @PreAuthorize("hasAnyAuthority('SCOPE_SinhVien', 'SCOPE_ChuTich')")
    @Operation(summary = "Danh sách đơn đăng ký CLB", description = "Xem danh sách tất cả đơn đăng ký vào CLB")
    public ApiResponse<List<RegisterResponse>> getClubRegistrations(@PathVariable Integer clubId) {
        return ApiResponse.<List<RegisterResponse>>builder()
                .result(leaderRegisterService.getClubRegistrations(clubId))
                .build();
    }

    /**
     * GET /api/registrations/club/{clubId}/status/{status}
     * Xem danh sách đơn đăng ký theo trạng thái
     */
    @GetMapping("/club/{clubId}/status/{status}")
    @PreAuthorize("hasAnyAuthority('SCOPE_SinhVien', 'SCOPE_ChuTich')")
    @Operation(summary = "Danh sách đơn theo trạng thái", description = "Xem danh sách đơn đăng ký theo trạng thái (ChoDuyet, DaDuyet, TuChoi...)")
    public ApiResponse<List<RegisterResponse>> getClubRegistrationsByStatus(
            @PathVariable Integer clubId,
            @PathVariable JoinStatus status) {
        return ApiResponse.<List<RegisterResponse>>builder()
                .result(leaderRegisterService.getClubRegistrationsByStatus(clubId, status))
                .build();
    }

    /**
     * PUT /api/registrations/approve
     * Duyệt đơn (DaDuyet) hoặc Từ chối (TuChoi)
     */
    @PutMapping("/approve")
    @PreAuthorize("hasAnyAuthority('SCOPE_SinhVien', 'SCOPE_ChuTich')")
    @Operation(summary = "Duyệt/Từ chối đơn", description = "Duyệt đơn (DaDuyet) hoặc Từ chối (TuChoi)")
    public ApiResponse<RegisterResponse> approveRegistration(@Valid @RequestBody ApproveRegisterRequest request) {
        String message = request.getStatus() == JoinStatus.DaDuyet 
                ? "Đã duyệt đơn đăng ký thành công" 
                : "Đã từ chối đơn đăng ký";
        return ApiResponse.<RegisterResponse>builder()
                .result(leaderRegisterService.approveRegistration(request))
                .message(message)
                .build();
    }

    /**
     * PUT /api/registrations/confirm-payment
     * Xác nhận sinh viên đã đóng tiền
     */
    @PutMapping("/confirm-payment")
    @PreAuthorize("hasAnyAuthority('SCOPE_SinhVien', 'SCOPE_ChuTich')")
    @Operation(summary = "Xác nhận thanh toán", description = "Xác nhận sinh viên đã đóng tiền")
    public ApiResponse<RegisterResponse> confirmPayment(@Valid @RequestBody ConfirmPaymentRequest request) {
        return ApiResponse.<RegisterResponse>builder()
                .result(leaderRegisterService.confirmPayment(request))
                .message("Xác nhận thanh toán thành công. Sinh viên đã trở thành thành viên chính thức!")
                .build();
    }
    
    /**
     * PUT /api/registrations/{regId}/role
     * Thăng chức/Hạ chức thành viên (VD: Từ ThanhVien lên PhoChuTich)
     */
    @PutMapping("/{regId}/role")
    @PreAuthorize("hasAnyAuthority('SCOPE_SinhVien', 'SCOPE_ChuTich')")
    @Operation(summary = "Thăng/hạ chức thành viên", description = "Thăng chức/Hạ chức thành viên (VD: Từ ThanhVien lên PhoChuTich)")
    public ApiResponse<RegisterResponse> changeRole(
            @PathVariable Integer regId,
            @Valid @RequestBody ChangeRoleRequest request) {
        
        RegisterResponse response = leaderRegisterService.changeRole(regId, request);
        
        return ApiResponse.<RegisterResponse>builder()
                .result(response)
                .message("Thay đổi vai trò thành công")
                .build();
    }
    
    /**
     * DELETE /api/registrations/{regId}
     * Xóa thành viên khỏi CLB (Kick) - đánh dấu DaRoiCLB
     */
    @DeleteMapping("/{regId}")
    @PreAuthorize("hasAnyAuthority('SCOPE_SinhVien', 'SCOPE_ChuTich')")
    @Operation(summary = "Kick thành viên", description = "Xóa thành viên khỏi CLB (Kick) - đánh dấu DaRoiCLB")
    public ApiResponse<Void> kickMember(@PathVariable Integer regId) {
        leaderRegisterService.kickMember(regId);
        
        return ApiResponse.<Void>builder()
                .message("Đã xóa thành viên khỏi CLB thành công")
                .build();
    }
}

