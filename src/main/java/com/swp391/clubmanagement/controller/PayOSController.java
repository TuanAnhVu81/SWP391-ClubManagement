package com.swp391.clubmanagement.controller;

import com.swp391.clubmanagement.dto.request.CreatePaymentLinkRequest;
import com.swp391.clubmanagement.dto.request.PayOSCreatePaymentRequest;
import com.swp391.clubmanagement.dto.response.ApiResponse;
import com.swp391.clubmanagement.dto.response.PaymentLinkResponse;
import com.swp391.clubmanagement.dto.response.PayOSPaymentLinkResponse;
import com.swp391.clubmanagement.dto.response.PayOSWebhookData;
import com.swp391.clubmanagement.entity.Registers;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.enums.JoinStatus;
import com.swp391.clubmanagement.exception.AppException;
import com.swp391.clubmanagement.exception.ErrorCode;
import com.swp391.clubmanagement.repository.RegisterRepository;
import com.swp391.clubmanagement.repository.UserRepository;
import com.swp391.clubmanagement.service.PayOSService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Payment", description = "APIs xử lý thanh toán PayOS")
@Slf4j
public class PayOSController {
    
    PayOSService payOSService;
    RegisterRepository registerRepository;
    UserRepository userRepository;
    
    @NonFinal
    @Value("${app.base-url}")
    String baseUrl;
    
    /**
     * Lấy user hiện tại từ SecurityContext
     */
    private Users getCurrentUser() {
        var context = SecurityContextHolder.getContext();
        String email = context.getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * POST /api/payments/create-link
     * Tạo payment link cho đăng ký CLB
     */
    @PostMapping("/create-link")
    @PreAuthorize("hasAnyAuthority('SCOPE_SinhVien', 'SCOPE_ChuTich')")
    @Operation(summary = "Tạo payment link", 
               description = "Tạo link thanh toán PayOS cho đăng ký CLB")
    public ApiResponse<PaymentLinkResponse> createPaymentLink(@Valid @RequestBody CreatePaymentLinkRequest request) {
        // Lấy user hiện tại
        Users currentUser = getCurrentUser();
        
        // Lấy thông tin đăng ký
        Registers register = registerRepository.findById(request.getSubscriptionId())
                .orElseThrow(() -> new AppException(ErrorCode.REGISTER_NOT_FOUND));
        
        // Kiểm tra quyền: chỉ user sở hữu register mới được tạo payment link
        if (!register.getUser().getUserId().equals(currentUser.getUserId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        
        // Kiểm tra đã thanh toán chưa
        if (Boolean.TRUE.equals(register.getIsPaid())) {
            throw new AppException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }
        
        // Kiểm tra đã được duyệt chưa
        if (register.getStatus() != JoinStatus.DaDuyet) {
            throw new AppException(ErrorCode.INVALID_APPLICATION_STATUS);
        }
        
        // Kiểm tra price hợp lệ
        if (register.getMembershipPackage().getPrice() == null || 
            register.getMembershipPackage().getPrice().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        
        // Tạo order code từ subscription ID (đảm bảo unique)
        // PayOS yêu cầu orderCode là số nguyên dương, tối đa 19 chữ số
        // Sử dụng subscriptionId * 10000 + timestamp (4 chữ số cuối) để tránh duplicate
        // Thêm random để đảm bảo unique hơn
        long timestamp = System.currentTimeMillis();
        int randomSuffix = (int)(Math.random() * 1000); // 0-999
        int orderCode = register.getSubscriptionId() * 10000 + (int)(timestamp % 10000) + randomSuffix;
        
        // Đảm bảo orderCode là số dương và không quá lớn
        if (orderCode <= 0) {
            orderCode = Math.abs(orderCode) + 1;
        }
        
        // Kiểm tra orderCode đã tồn tại chưa (tránh duplicate)
        int maxRetries = 5;
        int retryCount = 0;
        while (registerRepository.findByPayosOrderCode((long) orderCode).isPresent() && retryCount < maxRetries) {
            orderCode = register.getSubscriptionId() * 10000 + (int)(timestamp % 10000) + (int)(Math.random() * 1000);
            retryCount++;
        }
        
        if (retryCount >= maxRetries) {
            log.error("Failed to generate unique orderCode after {} retries", maxRetries);
            throw new AppException(ErrorCode.PAYMENT_LINK_CREATION_FAILED, "Không thể tạo mã đơn hàng duy nhất");
        }
        
        // Tính toán amount (chuyển từ BigDecimal sang int - VND)
        // PayOS yêu cầu amount là số nguyên (VND)
        // Price trong DB đã là VND (ví dụ: 10000.00 = 10,000 VND), chuyển trực tiếp
        int amount = register.getMembershipPackage().getPrice().intValue();
        
        // Tạo payment request
        // PayOS yêu cầu returnUrl và cancelUrl phải là URL hợp lệ (không chấp nhận localhost)
        // PayOS sẽ redirect về returnUrl với query params: code, id, cancel, status, orderCode
        String returnUrl = baseUrl.contains("localhost") 
                ? "https://pay.payos.vn" 
                : baseUrl + "/payments/success";
        String cancelUrl = baseUrl.contains("localhost") 
                ? "https://pay.payos.vn" 
                : baseUrl + "/payments/cancel";
        
        // Tính expiredAt (Unix timestamp - số giây từ 1970-01-01)
        // PayOS yêu cầu expiredAt là timestamp trong tương lai
        long expiredAt = Instant.now().plusSeconds(3600).getEpochSecond();
        
        PayOSCreatePaymentRequest payOSRequest = PayOSCreatePaymentRequest.builder()
                .orderCode(orderCode)
                .amount(amount)
                .description("Thanh toán đăng ký CLB: " + register.getMembershipPackage().getPackageName())
                .returnUrl(returnUrl)
                .cancelUrl(cancelUrl)
                .expiredAt(expiredAt)
                .build();
        
        log.info("PayOS request: orderCode={}, amount={}, returnUrl={}, cancelUrl={}", 
                orderCode, amount, returnUrl, cancelUrl);
        
        // Gọi PayOS API
        PayOSPaymentLinkResponse payOSResponse;
        try {
            payOSResponse = payOSService.createPaymentLink(payOSRequest);
        } catch (Exception e) {
            log.error("Error calling PayOS API for subscriptionId: {}", register.getSubscriptionId(), e);
            throw e; // Re-throw để GlobalExceptionHandler xử lý
        }
        
        // Validate response
        if (payOSResponse == null || payOSResponse.getData() == null) {
            log.error("PayOS response is null or data is null for subscriptionId: {}", register.getSubscriptionId());
            throw new AppException(ErrorCode.PAYMENT_LINK_CREATION_FAILED, 
                    "PayOS returned invalid response");
        }
        
        // Validate required fields
        if (payOSResponse.getData().getOrderCode() == null || payOSResponse.getData().getPaymentLinkId() == null) {
            log.error("PayOS response missing required fields: orderCode={}, paymentLinkId={}", 
                    payOSResponse.getData().getOrderCode(), payOSResponse.getData().getPaymentLinkId());
            throw new AppException(ErrorCode.PAYMENT_LINK_CREATION_FAILED, 
                    "PayOS response missing required fields");
        }
        
        // Lưu thông tin PayOS vào register
        // Lưu orderCode từ PayOS response (có thể khác với orderCode ta gửi lên)
        register.setPayosOrderCode(payOSResponse.getData().getOrderCode());
        register.setPayosPaymentLinkId(payOSResponse.getData().getPaymentLinkId());
        registerRepository.save(register);
        
        log.info("Saved PayOS info to register: subscriptionId={}, orderCode={}, paymentLinkId={}", 
                register.getSubscriptionId(), 
                payOSResponse.getData().getOrderCode(), 
                payOSResponse.getData().getPaymentLinkId());
        
        // Tạo response
        PaymentLinkResponse response = PaymentLinkResponse.builder()
                .paymentLink("https://pay.payos.vn/web/" + payOSResponse.getData().getPaymentLinkId())
                .qrCode(payOSResponse.getData().getQrCode())
                .orderCode(payOSResponse.getData().getOrderCode())
                .paymentLinkId(payOSResponse.getData().getPaymentLinkId())
                .build();
        
        return ApiResponse.<PaymentLinkResponse>builder()
                .result(response)
                .message("Tạo payment link thành công")
                .build();
    }

    /**
     * POST /api/payments/webhook
     * Webhook endpoint để nhận callback từ PayOS
     */
    @PostMapping("/webhook")
    @Operation(summary = "PayOS Webhook", 
               description = "Endpoint nhận callback từ PayOS khi thanh toán thành công")
    public ApiResponse<String> handleWebhook(@RequestBody PayOSWebhookData webhookData) {
        log.info("Received webhook from PayOS: {}", webhookData);
        
        try {
            // Verify signature
            boolean isValid = payOSService.verifyWebhookSignature(
                    webhookData.getCode(),
                    webhookData.getDesc(),
                    webhookData.getData(),
                    webhookData.getSignature()
            );
            
            if (!isValid) {
                log.error("Invalid webhook signature");
                throw new AppException(ErrorCode.INVALID_PAYMENT_SIGNATURE);
            }
            
            // Kiểm tra code = "00" (thành công)
            if (!"00".equals(webhookData.getCode())) {
                log.warn("Payment failed with code: {}, desc: {}", 
                        webhookData.getCode(), webhookData.getDesc());
                return ApiResponse.<String>builder()
                        .result("Payment failed")
                        .message(webhookData.getDesc())
                        .build();
            }
            
            // Tìm register theo orderCode
            Long orderCode = webhookData.getData().getOrderCode();
            Registers register = registerRepository.findByPayosOrderCode(orderCode)
                    .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
            
            // Kiểm tra đã thanh toán chưa
            if (Boolean.TRUE.equals(register.getIsPaid())) {
                log.warn("Payment already processed for orderCode: {}", orderCode);
                return ApiResponse.<String>builder()
                        .result("Payment already processed")
                        .message("Giao dịch đã được xử lý trước đó")
                        .build();
            }
            
            // Kiểm tra amount có khớp không (tránh fraud)
            // Price trong DB đã là VND, không cần nhân 1000
            int expectedAmount = register.getMembershipPackage().getPrice().intValue();
            if (expectedAmount != webhookData.getData().getAmount()) {
                log.error("Amount mismatch for orderCode: {}. Expected: {}, Received: {}", 
                        orderCode,
                        expectedAmount,
                        webhookData.getData().getAmount());
                throw new AppException(ErrorCode.INVALID_PAYMENT_SIGNATURE);
            }
            
            // Cập nhật thông tin thanh toán
            register.setIsPaid(true);
            register.setPaymentDate(LocalDateTime.now());
            register.setPaymentMethod("PayOS");
            register.setPayosReference(webhookData.getData().getReference());
            registerRepository.save(register);
            
            log.info("Payment processed successfully for subscriptionId: {}, orderCode: {}", 
                    register.getSubscriptionId(), orderCode);
            
            return ApiResponse.<String>builder()
                    .result("Payment processed successfully")
                    .message("Thanh toán thành công")
                    .build();
                    
        } catch (Exception e) {
            log.error("Error processing webhook", e);
            if (e instanceof AppException) {
                throw e;
            }
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    /**
     * GET /api/payments/success
     * Redirect sau khi thanh toán thành công
     * PayOS sẽ redirect về đây với query params: code, id, cancel, status, orderCode
     */
    @GetMapping("/success")
    @Operation(summary = "Payment Success", 
               description = "Redirect page sau khi thanh toán thành công. PayOS redirect với query params: code, id, cancel, status, orderCode")
    public ApiResponse<String> paymentSuccess(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String id,
            @RequestParam(required = false) Boolean cancel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long orderCode,
            @RequestParam(required = false) Integer subscriptionId) { // Giữ lại để backward compatibility
        
        log.info("Payment return URL called: code={}, id={}, cancel={}, status={}, orderCode={}, subscriptionId={}", 
                code, id, cancel, status, orderCode, subscriptionId);
        
        // Tìm register theo orderCode (ưu tiên) hoặc subscriptionId
        Registers register = null;
        if (orderCode != null) {
            register = registerRepository.findByPayosOrderCode(orderCode)
                    .orElse(null);
        }
        if (register == null && subscriptionId != null) {
            register = registerRepository.findById(subscriptionId)
                    .orElse(null);
        }
        
        if (register == null) {
            log.warn("Register not found for orderCode={} or subscriptionId={}", orderCode, subscriptionId);
            return ApiResponse.<String>builder()
                    .result("Payment info not found")
                    .message("Không tìm thấy thông tin thanh toán. Vui lòng liên hệ hỗ trợ.")
                    .build();
        }
        
        // Kiểm tra trạng thái từ PayOS
        if (cancel != null && cancel) {
            return ApiResponse.<String>builder()
                    .result("Payment cancelled")
                    .message("Bạn đã hủy thanh toán. Bạn có thể thanh toán lại sau.")
                    .build();
        }
        
        if ("PAID".equals(status) || "00".equals(code)) {
            // Thanh toán thành công
            if (Boolean.TRUE.equals(register.getIsPaid())) {
                return ApiResponse.<String>builder()
                        .result("Payment successful")
                        .message("Thanh toán thành công! Bạn đã trở thành thành viên của CLB.")
                        .build();
            } else {
                return ApiResponse.<String>builder()
                        .result("Payment pending")
                        .message("Thanh toán đang được xử lý. Vui lòng đợi trong giây lát.")
                        .build();
            }
        } else if ("PENDING".equals(status) || "PROCESSING".equals(status)) {
            return ApiResponse.<String>builder()
                    .result("Payment pending")
                    .message("Thanh toán đang được xử lý. Vui lòng đợi trong giây lát.")
                    .build();
        } else {
            return ApiResponse.<String>builder()
                    .result("Payment status unknown")
                    .message("Trạng thái thanh toán: " + (status != null ? status : "unknown"))
                    .build();
        }
    }

    /**
     * GET /api/payments/cancel
     * Redirect khi người dùng hủy thanh toán
     * PayOS có thể redirect về đây hoặc về success với cancel=true
     */
    @GetMapping("/cancel")
    @Operation(summary = "Payment Cancel", 
               description = "Redirect page khi người dùng hủy thanh toán")
    public ApiResponse<String> paymentCancel(
            @RequestParam(required = false) Long orderCode,
            @RequestParam(required = false) Integer subscriptionId) {
        
        log.info("Payment cancel URL called: orderCode={}, subscriptionId={}", orderCode, subscriptionId);
        
        return ApiResponse.<String>builder()
                .result("Payment cancelled")
                .message("Bạn đã hủy thanh toán. Bạn có thể thanh toán lại sau.")
                .build();
    }
}

