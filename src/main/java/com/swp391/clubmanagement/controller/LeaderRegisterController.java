package com.swp391.clubmanagement.controller;

import com.swp391.clubmanagement.dto.request.ApproveRegisterRequest;
import com.swp391.clubmanagement.dto.request.ConfirmPaymentRequest;
import com.swp391.clubmanagement.dto.response.ApiResponse;
import com.swp391.clubmanagement.dto.response.RegisterResponse;
import com.swp391.clubmanagement.enums.JoinStatus;
import com.swp391.clubmanagement.service.LeaderRegisterService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * LeaderRegisterController: API cho Chủ nhiệm CLB xử lý đơn đăng ký
 * 
 * Luồng: Xem đơn -> Duyệt/Từ chối -> Xác nhận thanh toán -> Thành viên chính thức
 */
@RestController
@RequestMapping("/leader/registers")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LeaderRegisterController {
    LeaderRegisterService leaderRegisterService;

    /**
     * API Xem danh sách tất cả đơn đăng ký vào CLB mình
     * Endpoint: GET /leader/registers/club/{clubId}
     */
    @GetMapping("/club/{clubId}")
    ApiResponse<List<RegisterResponse>> getClubRegistrations(@PathVariable Integer clubId) {
        return ApiResponse.<List<RegisterResponse>>builder()
                .result(leaderRegisterService.getClubRegistrations(clubId))
                .build();
    }

    /**
     * API Xem danh sách đơn đăng ký theo trạng thái
     * Endpoint: GET /leader/registers/club/{clubId}/status/{status}
     * Ví dụ: /leader/registers/club/1/status/ChoDuyet
     */
    @GetMapping("/club/{clubId}/status/{status}")
    ApiResponse<List<RegisterResponse>> getClubRegistrationsByStatus(
            @PathVariable Integer clubId,
            @PathVariable JoinStatus status) {
        return ApiResponse.<List<RegisterResponse>>builder()
                .result(leaderRegisterService.getClubRegistrationsByStatus(clubId, status))
                .build();
    }

    /**
     * API Duyệt đơn (DaDuyet) hoặc Từ chối (TuChoi)
     * Endpoint: PUT /leader/registers/approve
     * Body: { "subscriptionId": 1, "status": "DaDuyet" }
     */
    @PutMapping("/approve")
    ApiResponse<RegisterResponse> approveRegistration(@RequestBody ApproveRegisterRequest request) {
        String message = request.getStatus() == JoinStatus.DaDuyet 
                ? "Đã duyệt đơn đăng ký thành công" 
                : "Đã từ chối đơn đăng ký";
        return ApiResponse.<RegisterResponse>builder()
                .result(leaderRegisterService.approveRegistration(request))
                .message(message)
                .build();
    }

    /**
     * API Xác nhận sinh viên đã đóng tiền
     * Endpoint: PUT /leader/registers/confirm-payment
     * Body: { "subscriptionId": 1, "paymentMethod": "Cash" }
     */
    @PutMapping("/confirm-payment")
    ApiResponse<RegisterResponse> confirmPayment(@RequestBody ConfirmPaymentRequest request) {
        return ApiResponse.<RegisterResponse>builder()
                .result(leaderRegisterService.confirmPayment(request))
                .message("Xác nhận thanh toán thành công. Sinh viên đã trở thành thành viên chính thức!")
                .build();
    }
}

