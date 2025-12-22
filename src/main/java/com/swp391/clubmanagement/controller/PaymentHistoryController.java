package com.swp391.clubmanagement.controller;

import com.swp391.clubmanagement.dto.response.ApiResponse;
import com.swp391.clubmanagement.dto.response.PaymentHistoryResponse;
import com.swp391.clubmanagement.dto.response.RevenueResponse;
import com.swp391.clubmanagement.service.PaymentHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PaymentHistoryController - REST Controller xử lý lịch sử giao dịch và tính doanh thu
 */
@RestController
@RequestMapping("/payment-history")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Payment History", description = "APIs xem lịch sử giao dịch và tính doanh thu")
public class PaymentHistoryController {
    
    PaymentHistoryService paymentHistoryService;
    
    // ============ XEM LỊCH SỬ GIAO DỊCH ============
    
    /**
     * GET /api/payment-history/my-history
     * Xem lịch sử giao dịch của user hiện tại
     */
    @GetMapping("/my-history")
    @PreAuthorize("hasAnyAuthority('SCOPE_SinhVien', 'SCOPE_ChuTich')")
    @Operation(summary = "Xem lịch sử giao dịch của tôi", 
               description = "Xem lịch sử các giao dịch thanh toán của user hiện tại")
    public ApiResponse<Page<PaymentHistoryResponse>> getMyPaymentHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "paymentDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<PaymentHistoryResponse> result = paymentHistoryService.getMyPaymentHistory(pageable);
        
        return ApiResponse.<Page<PaymentHistoryResponse>>builder()
                .result(result)
                .message("Lấy lịch sử giao dịch thành công")
                .build();
    }
    
    /**
     * GET /api/payment-history/user/{userId}
     * Xem lịch sử giao dịch của một user (Admin/Leader)
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyAuthority('SCOPE_QuanTriVien', 'SCOPE_ChuTich')")
    @Operation(summary = "Xem lịch sử giao dịch của user", 
               description = "Xem lịch sử các giao dịch thanh toán của một user cụ thể (Admin/Leader)")
    public ApiResponse<Page<PaymentHistoryResponse>> getUserPaymentHistory(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "paymentDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<PaymentHistoryResponse> result = paymentHistoryService.getUserPaymentHistory(userId, pageable);
        
        return ApiResponse.<Page<PaymentHistoryResponse>>builder()
                .result(result)
                .message("Lấy lịch sử giao dịch thành công")
                .build();
    }
    
    /**
     * GET /api/payment-history/club/{clubId}
     * Xem lịch sử giao dịch của một CLB
     */
    @GetMapping("/club/{clubId}")
    @PreAuthorize("hasAnyAuthority('SCOPE_QuanTriVien', 'SCOPE_ChuTich')")
    @Operation(summary = "Xem lịch sử giao dịch của CLB", 
               description = "Xem lịch sử các giao dịch thanh toán của một CLB (Admin/Leader)")
    public ApiResponse<Page<PaymentHistoryResponse>> getClubPaymentHistory(
            @PathVariable Integer clubId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "paymentDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<PaymentHistoryResponse> result = paymentHistoryService.getClubPaymentHistory(clubId, pageable);
        
        return ApiResponse.<Page<PaymentHistoryResponse>>builder()
                .result(result)
                .message("Lấy lịch sử giao dịch thành công")
                .build();
    }
    
    // ============ TÍNH DOANH THU ============
    
    /**
     * GET /api/payment-history/revenue/club/{clubId}
     * Tính tổng doanh thu của một CLB
     */
    @GetMapping("/revenue/club/{clubId}")
    @PreAuthorize("hasAnyAuthority('SCOPE_QuanTriVien', 'SCOPE_ChuTich')")
    @Operation(summary = "Tính doanh thu của CLB", 
               description = "Tính tổng doanh thu của một CLB (tất cả thời gian)")
    public ApiResponse<RevenueResponse> getRevenueByClub(@PathVariable Integer clubId) {
        RevenueResponse result = paymentHistoryService.calculateRevenueByClub(clubId);
        
        return ApiResponse.<RevenueResponse>builder()
                .result(result)
                .message("Tính doanh thu thành công")
                .build();
    }
    
    /**
     * GET /api/payment-history/revenue/club/{clubId}/date-range
     * Tính doanh thu của một CLB trong khoảng thời gian
     */
    @GetMapping("/revenue/club/{clubId}/date-range")
    @PreAuthorize("hasAnyAuthority('SCOPE_QuanTriVien', 'SCOPE_ChuTich')")
    @Operation(summary = "Tính doanh thu của CLB theo khoảng thời gian", 
               description = "Tính doanh thu của một CLB trong khoảng thời gian cụ thể")
    public ApiResponse<RevenueResponse> getRevenueByClubAndDateRange(
            @PathVariable Integer clubId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        RevenueResponse result = paymentHistoryService.calculateRevenueByClubAndDateRange(
                clubId, startDate, endDate);
        
        return ApiResponse.<RevenueResponse>builder()
                .result(result)
                .message("Tính doanh thu thành công")
                .build();
    }
    
    /**
     * GET /api/payment-history/revenue/package/{packageId}
     * Tính doanh thu của một gói membership
     */
    @GetMapping("/revenue/package/{packageId}")
    @PreAuthorize("hasAnyAuthority('SCOPE_QuanTriVien', 'SCOPE_ChuTich')")
    @Operation(summary = "Tính doanh thu của gói membership", 
               description = "Tính tổng doanh thu của một gói membership (tất cả thời gian)")
    public ApiResponse<RevenueResponse> getRevenueByPackage(@PathVariable Integer packageId) {
        RevenueResponse result = paymentHistoryService.calculateRevenueByPackage(packageId);
        
        return ApiResponse.<RevenueResponse>builder()
                .result(result)
                .message("Tính doanh thu thành công")
                .build();
    }
    
    /**
     * GET /api/payment-history/revenue/package/{packageId}/date-range
     * Tính doanh thu của một gói membership trong khoảng thời gian
     */
    @GetMapping("/revenue/package/{packageId}/date-range")
    @PreAuthorize("hasAnyAuthority('SCOPE_QuanTriVien', 'SCOPE_ChuTich')")
    @Operation(summary = "Tính doanh thu của gói membership theo khoảng thời gian", 
               description = "Tính doanh thu của một gói membership trong khoảng thời gian cụ thể")
    public ApiResponse<RevenueResponse> getRevenueByPackageAndDateRange(
            @PathVariable Integer packageId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        RevenueResponse result = paymentHistoryService.calculateRevenueByPackageAndDateRange(
                packageId, startDate, endDate);
        
        return ApiResponse.<RevenueResponse>builder()
                .result(result)
                .message("Tính doanh thu thành công")
                .build();
    }
    
    /**
     * GET /api/payment-history/revenue/total/date-range
     * Tính tổng doanh thu trong khoảng thời gian
     */
    @GetMapping("/revenue/total/date-range")
    @PreAuthorize("hasAuthority('SCOPE_QuanTriVien')")
    @Operation(summary = "Tính tổng doanh thu theo khoảng thời gian", 
               description = "Tính tổng doanh thu của tất cả CLB trong khoảng thời gian (Admin only)")
    public ApiResponse<RevenueResponse> getTotalRevenueByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        RevenueResponse result = paymentHistoryService.calculateTotalRevenueByDateRange(startDate, endDate);
        
        return ApiResponse.<RevenueResponse>builder()
                .result(result)
                .message("Tính doanh thu thành công")
                .build();
    }
    
    /**
     * GET /api/payment-history/revenue/by-club/date-range
     * Tính doanh thu theo từng CLB (group by club)
     */
    @GetMapping("/revenue/by-club/date-range")
    @PreAuthorize("hasAuthority('SCOPE_QuanTriVien')")
    @Operation(summary = "Tính doanh thu theo từng CLB", 
               description = "Tính doanh thu của từng CLB trong khoảng thời gian, sắp xếp theo doanh thu giảm dần (Admin only)")
    public ApiResponse<List<RevenueResponse>> getRevenueByClubGrouped(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        List<RevenueResponse> result = paymentHistoryService.calculateRevenueByClubGrouped(startDate, endDate);
        
        return ApiResponse.<List<RevenueResponse>>builder()
                .result(result)
                .message("Tính doanh thu thành công")
                .build();
    }
    
    /**
     * GET /api/payment-history/revenue/club/{clubId}/by-package/date-range
     * Tính doanh thu theo từng gói membership của một CLB
     */
    @GetMapping("/revenue/club/{clubId}/by-package/date-range")
    @PreAuthorize("hasAnyAuthority('SCOPE_QuanTriVien', 'SCOPE_ChuTich')")
    @Operation(summary = "Tính doanh thu theo từng gói của CLB", 
               description = "Tính doanh thu của từng gói membership trong một CLB, sắp xếp theo doanh thu giảm dần")
    public ApiResponse<List<RevenueResponse>> getRevenueByPackageForClub(
            @PathVariable Integer clubId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        List<RevenueResponse> result = paymentHistoryService.calculateRevenueByPackageForClub(
                clubId, startDate, endDate);
        
        return ApiResponse.<List<RevenueResponse>>builder()
                .result(result)
                .message("Tính doanh thu thành công")
                .build();
    }
    
    /**
     * GET /api/payment-history/revenue/by-month/date-range
     * Tính doanh thu theo tháng
     */
    @GetMapping("/revenue/by-month/date-range")
    @PreAuthorize("hasAuthority('SCOPE_QuanTriVien')")
    @Operation(summary = "Tính doanh thu theo tháng", 
               description = "Tính doanh thu theo từng tháng trong khoảng thời gian (Admin only)")
    public ApiResponse<List<RevenueResponse>> getRevenueByMonth(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        List<RevenueResponse> result = paymentHistoryService.calculateRevenueByMonth(startDate, endDate);
        
        return ApiResponse.<List<RevenueResponse>>builder()
                .result(result)
                .message("Tính doanh thu thành công")
                .build();
    }
}

