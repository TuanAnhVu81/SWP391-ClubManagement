package com.swp391.clubmanagement.controller;

import com.swp391.clubmanagement.dto.response.ApiResponse;
import com.swp391.clubmanagement.dto.response.MembershipResponse;
import com.swp391.clubmanagement.service.MembershipService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MembershipController: API xem các gói thành viên của CLB
 */
@RestController
@RequestMapping("/memberships")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MembershipController {
    MembershipService membershipService;

    /**
     * API Xem danh sách các gói thành viên của 1 CLB
     * Endpoint: GET /memberships/club/{clubId}
     */
    @GetMapping("/club/{clubId}")
    ApiResponse<List<MembershipResponse>> getPackagesByClub(@PathVariable Integer clubId) {
        return ApiResponse.<List<MembershipResponse>>builder()
                .result(membershipService.getPackagesByClub(clubId))
                .build();
    }

    /**
     * API Xem chi tiết 1 gói
     * Endpoint: GET /memberships/{packageId}
     */
    @GetMapping("/{packageId}")
    ApiResponse<MembershipResponse> getPackageById(@PathVariable Integer packageId) {
        return ApiResponse.<MembershipResponse>builder()
                .result(membershipService.getPackageById(packageId))
                .build();
    }
}

