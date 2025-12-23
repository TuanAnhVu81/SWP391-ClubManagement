package com.swp391.clubmanagement.controller;

import com.swp391.clubmanagement.dto.response.ApiResponse;
import com.swp391.clubmanagement.dto.response.PaymentHistoryResponse;
import com.swp391.clubmanagement.dto.response.RevenueResponse;
import com.swp391.clubmanagement.dto.response.RevenueByMonthWithClubsResponse;
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
     * Tính doanh thu của một CLB theo tháng
     */
    @GetMapping("/revenue/club/{clubId}/date-range")
    @PreAuthorize("hasAnyAuthority('SCOPE_QuanTriVien', 'SCOPE_ChuTich')")
    @Operation(summary = "Tính doanh thu của CLB theo tháng", 
               description = "Tính doanh thu của một CLB theo từng tháng trong khoảng thời gian")
    public ApiResponse<List<RevenueResponse>> getRevenueByClubAndDateRange(
            @PathVariable Integer clubId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        List<RevenueResponse> result = paymentHistoryService.calculateRevenueByClubByMonth(
                clubId, startDate, endDate);
        
        return ApiResponse.<List<RevenueResponse>>builder()
                .result(result)
                .message("Tính doanh thu thành công")
                .build();
    }
    
    /**
     * GET /api/payment-history/revenue/by-month/date-range
     * Tính doanh thu theo tháng kèm danh sách doanh thu từng CLB
     */
    @GetMapping("/revenue/by-month/date-range")
    @PreAuthorize("hasAuthority('SCOPE_QuanTriVien')")
    @Operation(summary = "Tính doanh thu theo tháng kèm danh sách doanh thu từng CLB", 
               description = "Tính doanh thu theo từng tháng trong khoảng thời gian, bao gồm danh sách doanh thu từng CLB và tổng doanh thu (Admin only)")
    public ApiResponse<List<RevenueByMonthWithClubsResponse>> getRevenueByMonth(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        List<RevenueByMonthWithClubsResponse> result = paymentHistoryService.calculateRevenueByMonthWithClubs(startDate, endDate);
        
        return ApiResponse.<List<RevenueByMonthWithClubsResponse>>builder()
                .result(result)
                .message("Tính doanh thu thành công")
                .build();
    }
}

