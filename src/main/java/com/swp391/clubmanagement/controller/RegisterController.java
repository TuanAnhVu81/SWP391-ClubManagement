package com.swp391.clubmanagement.controller;

import com.swp391.clubmanagement.dto.request.JoinClubRequest;
import com.swp391.clubmanagement.dto.response.ApiResponse;
import com.swp391.clubmanagement.dto.response.RegisterResponse;
import com.swp391.clubmanagement.service.RegisterService;
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
 * RegisterController: API cho sinh viên đăng ký tham gia CLB
 * 
 * Luồng: Sinh viên mua gói -> Chờ duyệt -> Đóng tiền -> Thành member
 */
@RestController
@RequestMapping("/registers")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Student Registration", description = "APIs cho sinh viên đăng ký tham gia CLB")
public class RegisterController {
    RegisterService registerService;

    /**
     * POST /api/registers
     * Đăng ký tham gia CLB (chọn gói membership)
     * 
     * @param request { "packageId": 1, "joinReason": "Lý do muốn gia nhập CLB" }
     * @return Thông tin đăng ký. Trạng thái mặc định: ChoDuyet (chờ Leader duyệt)
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_SinhVien', 'SCOPE_ChuTich')")
    @Operation(summary = "Đăng ký tham gia CLB", 
               description = "Sinh viên đăng ký tham gia CLB bằng cách chọn 1 gói membership và điền lý do gia nhập. Lý do gia nhập phải từ 20-500 ký tự. Trạng thái ban đầu: ChoDuyet")
    public ApiResponse<RegisterResponse> joinClub(@Valid @RequestBody JoinClubRequest request) {
        return ApiResponse.<RegisterResponse>builder()
                .result(registerService.joinClub(request))
                .message("Đăng ký thành công! Vui lòng chờ Leader CLB duyệt.")
                .build();
    }

    /**
     * API Xem danh sách các CLB mình đã đăng ký và trạng thái
     * Endpoint: GET /registers/my-registrations
     */
    @GetMapping("/my-registrations")
    @PreAuthorize("hasAnyAuthority('SCOPE_SinhVien', 'SCOPE_ChuTich')")
    @Operation(summary = "Danh sách đăng ký của tôi", 
               description = "Xem danh sách tất cả các CLB mà sinh viên đã đăng ký tham gia và trạng thái của từng đăng ký (ChoDuyet, DaDuyet, TuChoi, DaRoiCLB).")
    ApiResponse<List<RegisterResponse>> getMyRegistrations() {
        return ApiResponse.<List<RegisterResponse>>builder()
                .result(registerService.getMyRegistrations())
                .build();
    }

    /**
     * GET /api/registers/{subscriptionId}
     * Xem chi tiết 1 đăng ký
     * 
     * @param subscriptionId ID của đăng ký
     * @return Thông tin đăng ký chi tiết
     */
    @GetMapping("/{subscriptionId}")
    @PreAuthorize("hasAnyAuthority('SCOPE_SinhVien', 'SCOPE_ChuTich')")
    @Operation(summary = "Chi tiết đăng ký", 
               description = "Xem chi tiết 1 đăng ký CLB")
    public ApiResponse<RegisterResponse> getRegistrationById(@PathVariable Integer subscriptionId) {
        return ApiResponse.<RegisterResponse>builder()
                .result(registerService.getRegistrationById(subscriptionId))
                .build();
    }

    /**
     * DELETE /api/registers/{subscriptionId}
     * Hủy đăng ký CLB
     * 
     * @param subscriptionId ID của đăng ký cần hủy
     * @return Thành công. Chỉ hủy được khi trạng thái còn ChoDuyet (chưa được duyệt)
     */
    @DeleteMapping("/{subscriptionId}")
    @PreAuthorize("hasAnyAuthority('SCOPE_SinhVien', 'SCOPE_ChuTich')")
    @Operation(summary = "Hủy đăng ký", 
               description = "Hủy đăng ký CLB. Chỉ hủy được khi trạng thái còn ChoDuyet (chưa được Leader duyệt)")
    public ApiResponse<String> cancelRegistration(@PathVariable Integer subscriptionId) {
        registerService.cancelRegistration(subscriptionId);
        return ApiResponse.<String>builder()
                .result("Hủy đăng ký thành công")
                .build();
    }

    /**
     * POST /api/registers/{clubId}/leave
     * Rời khỏi CLB (chỉ dành cho sinh viên)
     * 
     * @param clubId ID của CLB muốn rời
     * @return Thành công. Chỉ sinh viên (không phải ChuTich) mới được rời CLB
     */
    @PostMapping("/{clubId}/leave")
    @PreAuthorize("hasAuthority('SCOPE_SinhVien')")
    @Operation(summary = "Rời khỏi CLB", 
               description = "Sinh viên rời khỏi CLB mà mình đang tham gia. Chỉ dành cho sinh viên (không dành cho ChuTich). User phải là thành viên đang active (DaDuyet + đã thanh toán) mới có thể rời.")
    public ApiResponse<String> leaveClub(@PathVariable Integer clubId) {
        registerService.leaveClub(clubId);
        return ApiResponse.<String>builder()
                .result("Rời CLB thành công")
                .build();
    }
}

