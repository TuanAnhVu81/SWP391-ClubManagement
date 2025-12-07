package com.swp391.clubmanagement.controller;

import com.swp391.clubmanagement.dto.request.JoinClubRequest;
import com.swp391.clubmanagement.dto.response.ApiResponse;
import com.swp391.clubmanagement.dto.response.RegisterResponse;
import com.swp391.clubmanagement.service.RegisterService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
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
public class RegisterController {
    RegisterService registerService;

    /**
     * API Đăng ký tham gia CLB (mua gói package)
     * Endpoint: POST /registers
     * Trạng thái mặc định: ChoDuyet
     */
    @PostMapping
    ApiResponse<RegisterResponse> joinClub(@RequestBody JoinClubRequest request) {
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
    ApiResponse<List<RegisterResponse>> getMyRegistrations() {
        return ApiResponse.<List<RegisterResponse>>builder()
                .result(registerService.getMyRegistrations())
                .build();
    }

    /**
     * API Xem chi tiết 1 đăng ký
     * Endpoint: GET /registers/{subscriptionId}
     */
    @GetMapping("/{subscriptionId}")
    ApiResponse<RegisterResponse> getRegistrationById(@PathVariable Integer subscriptionId) {
        return ApiResponse.<RegisterResponse>builder()
                .result(registerService.getRegistrationById(subscriptionId))
                .build();
    }

    /**
     * API Hủy đăng ký (chỉ khi trạng thái còn ChoDuyet)
     * Endpoint: DELETE /registers/{subscriptionId}
     */
    @DeleteMapping("/{subscriptionId}")
    ApiResponse<String> cancelRegistration(@PathVariable Integer subscriptionId) {
        registerService.cancelRegistration(subscriptionId);
        return ApiResponse.<String>builder()
                .result("Hủy đăng ký thành công")
                .build();
    }
}

