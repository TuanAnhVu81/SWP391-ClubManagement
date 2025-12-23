package com.swp391.clubmanagement.controller;

import com.swp391.clubmanagement.dto.request.ConfirmWebhookRequest;
import com.swp391.clubmanagement.dto.request.CreatePaymentLinkRequest;
import com.swp391.clubmanagement.dto.request.PayOSCreatePaymentRequest;
import com.swp391.clubmanagement.dto.response.ApiResponse;
import com.swp391.clubmanagement.dto.response.ConfirmWebhookResponse;
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
import com.swp391.clubmanagement.service.PaymentHistoryService;
import com.swp391.clubmanagement.service.PayOSService;
import com.swp391.clubmanagement.utils.DateTimeUtils;
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
    PaymentHistoryService paymentHistoryService;
    
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
     * 
     * Logic:
     * - Nếu đã có payment link đang pending (có payosOrderCode nhưng chưa thanh toán) -> trả về link đó
     * - Nếu chưa có -> tạo payment link mới
     * - Sử dụng pessimistic locking để tránh race condition khi tạo đồng thời
     */
    @PostMapping("/create-link")
    @PreAuthorize("hasAnyAuthority('SCOPE_SinhVien', 'SCOPE_ChuTich')")
    @Operation(summary = "Tạo payment link", 
               description = "Tạo link thanh toán PayOS cho đăng ký CLB. Nếu đã có payment link đang pending, trả về link đó.")
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<PaymentLinkResponse> createPaymentLink(@Valid @RequestBody CreatePaymentLinkRequest request) {
        // Lấy user hiện tại
        Users currentUser = getCurrentUser();
        
        // Lấy thông tin đăng ký với pessimistic lock để tránh race condition
        // Lock sẽ được giữ cho đến khi transaction kết thúc
        // Điều này đảm bảo chỉ một request có thể tạo payment link tại một thời điểm
        Registers register = registerRepository.findByIdWithLock(request.getSubscriptionId())
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
        
        // QUAN TRỌNG: Kiểm tra xem đã có payment link đang pending chưa
        // Nếu đã có payosOrderCode và payosPaymentLinkId nhưng chưa thanh toán -> trả về link đó
        if (register.getPayosOrderCode() != null && register.getPayosPaymentLinkId() != null) {
            log.info("Payment link already exists for subscriptionId: {}, orderCode: {}, paymentLinkId: {}. Returning existing link.",
                    register.getSubscriptionId(), register.getPayosOrderCode(), register.getPayosPaymentLinkId());
            
            // Trả về payment link đã tồn tại
            PaymentLinkResponse response = PaymentLinkResponse.builder()
                    .paymentLink("https://pay.payos.vn/web/" + register.getPayosPaymentLinkId())
                    .orderCode(register.getPayosOrderCode())
                    .paymentLinkId(register.getPayosPaymentLinkId())
                    .build();
            
            return ApiResponse.<PaymentLinkResponse>builder()
                    .result(response)
                    .message("Đã có payment link đang chờ thanh toán. Vui lòng sử dụng link này để thanh toán.")
                    .build();
        }
        
        // Tạo order code từ subscription ID (đảm bảo unique)
        // Lấy 6 số cuối của timestamp hiện tại để đảm bảo tính thời gian
        // Kết hợp với subscriptionId ở đầu để đảm bảo unique theo đơn hàng
        long timestamp = System.currentTimeMillis();
        String timeStr = String.valueOf(timestamp);
        String timeSuffix = timeStr.substring(timeStr.length() - 6);

        // Ví dụ: subId = 3, time = ...123456 -> orderCode = 3123456
        long orderCode = Long.parseLong(register.getSubscriptionId() + timeSuffix);
        
        // Đảm bảo orderCode là số dương
        if (orderCode <= 0) {
            orderCode = Math.abs(orderCode);
        }
        
        // Kiểm tra orderCode đã tồn tại chưa (tránh duplicate)
        int maxRetries = 5;
        int retryCount = 0;
        while (registerRepository.findByPayosOrderCode(orderCode).isPresent() && retryCount < maxRetries) {
            // Thêm random nếu bị trùng
            orderCode = timestamp * 100000L + register.getSubscriptionId() + (long)(Math.random() * 10000);
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
        String frontendUrl = "https://club-management-system-ochre.vercel.app";
        String returnUrl = frontendUrl + "/payment/success?subscriptionId=" + register.getSubscriptionId();
        String cancelUrl = frontendUrl + "/payment/cancel?subscriptionId=" + register.getSubscriptionId();
        
        // Tạo items array (required by PayOS API)
        // PayOS giới hạn description tối đa 25 ký tự
        String description = "Phi CLB" + register.getMembershipPackage().getPackageName();
        
        PayOSCreatePaymentRequest.ItemData item = PayOSCreatePaymentRequest.ItemData.builder()
                .name(register.getMembershipPackage().getPackageName())
                .quantity(1)
                .price(amount)
                .build();
        
        PayOSCreatePaymentRequest payOSRequest = PayOSCreatePaymentRequest.builder()
                .orderCode(orderCode)
                .amount(amount)
                .description(description)
                .items(java.util.Collections.singletonList(item))
                .returnUrl(returnUrl)
                .cancelUrl(cancelUrl)
                .build();
        
        log.info("=== PayOS Payment Request ===");
        log.info("OrderCode: {}", orderCode);
        log.info("Amount: {}", amount);
        log.info("Description: {}", description);
        log.info("Items: {}", payOSRequest.getItems());
        log.info("ReturnUrl: {}", returnUrl);
        log.info("CancelUrl: {}", cancelUrl);
        log.info("============================");
        
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
     * POST /api/payments/confirm-webhook
     * Confirm webhook URL với PayOS
     * Endpoint này để đăng ký webhook URL với PayOS
     */
    @PostMapping("/confirm-webhook")
    @PreAuthorize("hasAuthority('SCOPE_ChuTich')")
    @Operation(summary = "Confirm Webhook URL", 
               description = "Đăng ký webhook URL với PayOS (chỉ Chủ tịch được phép)")
    public ApiResponse<ConfirmWebhookResponse> confirmWebhook(@Valid @RequestBody ConfirmWebhookRequest request) {
        log.info("=== CONFIRM WEBHOOK REQUEST ===");
        log.info("Webhook URL: {}", request.getWebhookUrl());
        log.info("================================");
        
        try {
            ConfirmWebhookResponse response = payOSService.confirmWebhook(request.getWebhookUrl());
            
            return ApiResponse.<ConfirmWebhookResponse>builder()
                    .result(response)
                    .message("Webhook URL đã được xác nhận với PayOS thành công")
                    .build();
        } catch (Exception e) {
            log.error("❌ Error confirming webhook: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * POST/GET /api/payments/webhook
     * Webhook endpoint để nhận callback từ PayOS
     * GET: PayOS dùng để verify webhook URL khi save
     * POST: PayOS gửi thông tin thanh toán thực tế
     * 
     * Sử dụng @Transactional để đảm bảo thay đổi được commit vào database
     * và pessimistic lock hoạt động đúng cách
     */
    @RequestMapping(value = "/webhook", method = {RequestMethod.POST, RequestMethod.GET})
    @Operation(summary = "PayOS Webhook", 
               description = "Endpoint nhận callback từ PayOS khi thanh toán thành công (POST) hoặc verify (GET)")
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<String> handleWebhook(@RequestBody(required = false) PayOSWebhookData webhookData) {
        log.info("=== WEBHOOK RECEIVED ===");
        
        // Xử lý request test từ PayOS (khi save webhook URL)
        if (webhookData == null || webhookData.getData() == null) {
            log.info("Webhook test request from PayOS - returning success");
            return ApiResponse.<String>builder()
                    .result("OK")
                    .message("Webhook endpoint is active")
                    .build();
        }
        
        log.info("Code: {}", webhookData.getCode());
        log.info("Desc: {}", webhookData.getDesc());
        log.info("OrderCode: {}", webhookData.getData().getOrderCode());
        log.info("Amount: {}", webhookData.getData().getAmount());
        log.info("Signature: {}", webhookData.getSignature());
        log.info("========================");
        
        // Detect test webhook từ PayOS (khi confirm webhook URL)
        // PayOS gửi test data với orderCode nhỏ (thường là 123) và amount nhỏ (3000)
        Long orderCode = webhookData.getData().getOrderCode();
        Integer amount = webhookData.getData().getAmount();
        
        if (orderCode != null && orderCode <= 1000 && amount != null && amount <= 5000) {
            log.info("✅ Test webhook detected (orderCode={}, amount={}). Returning success without processing.", 
                    orderCode, amount);
            return ApiResponse.<String>builder()
                    .code(1000)
                    .result("OK")
                    .message("Webhook test successful")
                    .build();
        }
        
        try {
            // Verify signature (TẠM THỜI SKIP để test)
            // TODO: Bật lại sau khi test thành công
            /*
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
            */
            log.warn("SIGNATURE VERIFICATION SKIPPED FOR TESTING");
            
            // Kiểm tra code = "00" (thành công)
            if (!"00".equals(webhookData.getCode())) {
                log.warn("Payment failed with code: {}, desc: {}", 
                        webhookData.getCode(), webhookData.getDesc());
                return ApiResponse.<String>builder()
                        .result("Payment failed")
                        .message(webhookData.getDesc())
                        .build();
            }
            
            // Tìm register theo orderCode với pessimistic lock để tránh xử lý trùng lặp
            // Lock đảm bảo chỉ một webhook request có thể xử lý thanh toán tại một thời điểm
            log.info("Looking for register with orderCode: {}", orderCode);
            
            Registers registerFound = registerRepository.findByPayosOrderCode(orderCode)
                    .orElseThrow(() -> {
                        log.error("Register not found for orderCode: {}", orderCode);
                        return new AppException(ErrorCode.PAYMENT_NOT_FOUND);
                    });
            
            // Lưu subscriptionId trước khi lock
            Integer subscriptionId = registerFound.getSubscriptionId();
            
            // Lock register để tránh race condition khi xử lý webhook đồng thời
            // Reload với lock để đảm bảo dữ liệu mới nhất
            Registers register = registerRepository.findByIdWithLock(subscriptionId)
                    .orElseThrow(() -> {
                        log.error("Register not found after lock for subscriptionId: {}", subscriptionId);
                        return new AppException(ErrorCode.PAYMENT_NOT_FOUND);
                    });
            
            log.info("Found register: subscriptionId={}, isPaid={}", 
                    register.getSubscriptionId(), register.getIsPaid());
            
            // Kiểm tra đã thanh toán chưa (double-check sau khi lock)
            if (Boolean.TRUE.equals(register.getIsPaid())) {
                log.warn("Payment already processed for orderCode: {}. This is a duplicate webhook call.", orderCode);
                return ApiResponse.<String>builder()
                        .result("Payment already processed")
                        .message("Giao dịch đã được xử lý trước đó")
                        .build();
            }
            
            // Kiểm tra amount có khớp không (tránh fraud)
            int expectedAmount = register.getMembershipPackage().getPrice().intValue();
            int receivedAmount = webhookData.getData().getAmount();
            log.info("Amount check: expected={}, received={}", expectedAmount, receivedAmount);
            
            if (expectedAmount != receivedAmount) {
                log.error("Amount mismatch for orderCode: {}. Expected: {}, Received: {}", 
                        orderCode, expectedAmount, receivedAmount);
                throw new AppException(ErrorCode.INVALID_PAYMENT_SIGNATURE);
            }
            
            // Cập nhật thông tin thanh toán
            log.info("Updating register to PAID status...");
            LocalDateTime now = DateTimeUtils.nowVietnam();
            register.setIsPaid(true);
            register.setPaymentDate(now);
            register.setPaymentMethod("PayOS");
            register.setPayosReference(webhookData.getData().getReference());
            
            // Set thời gian bắt đầu và kết thúc membership dựa trên term của gói
            LocalDateTime startDate = now;
            register.setStartDate(startDate);
            
            // Tính endDate dựa trên term của package
            String term = register.getMembershipPackage().getTerm();
            LocalDateTime endDate = calculateEndDate(startDate, term);
            register.setEndDate(endDate);
            
            // Save và flush để đảm bảo thay đổi được commit ngay lập tức
            register = registerRepository.save(register);
            registerRepository.flush(); // Force flush to database
            
            log.info("✅ Payment processed successfully for subscriptionId: {}, orderCode: {}, isPaid: {}, membership valid until: {}", 
                    register.getSubscriptionId(), orderCode, register.getIsPaid(), endDate);
            
            // Tạo payment history record
            paymentHistoryService.createPaymentHistory(register);
            log.info("Payment history created for subscriptionId: {}", register.getSubscriptionId());
            
            return ApiResponse.<String>builder()
                    .result("Payment processed successfully")
                    .message("Thanh toán thành công")
                    .build();
                    
        } catch (Exception e) {
            log.error("❌ Error processing webhook: {}", e.getMessage(), e);
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
    
    /**
     * Helper method: Tính toán endDate dựa trên term của gói membership
     * @param startDate Ngày bắt đầu
     * @param term Kỳ hạn (VD: "1 tháng", "3 tháng", "6 tháng", "1 năm")
     * @return Ngày hết hạn
     */
    private LocalDateTime calculateEndDate(LocalDateTime startDate, String term) {
        if (term == null || term.isEmpty()) {
            // Mặc định 1 năm nếu không có term
            log.warn("Term is null or empty. Using default 1 year");
            return startDate.plusYears(1);
        }
        
        // Chuyển về lowercase và trim để dễ xử lý
        String normalizedTerm = term.toLowerCase().trim();
        
        // Parse term và tính endDate
        if (normalizedTerm.contains("tháng")) {
            // Trích xuất số tháng (VD: "1 tháng", "3 tháng", "6 tháng")
            try {
                String[] parts = normalizedTerm.split("\\s+");
                int months = Integer.parseInt(parts[0]);
                log.info("Calculated end date: {} months from {}", months, startDate);
                return startDate.plusMonths(months);
            } catch (Exception e) {
                log.warn("Cannot parse term: {}. Using default 6 months", term);
                return startDate.plusMonths(6);
            }
        } else if (normalizedTerm.contains("năm")) {
            // Trích xuất số năm (VD: "1 năm", "2 năm")
            try {
                String[] parts = normalizedTerm.split("\\s+");
                int years = Integer.parseInt(parts[0]);
                log.info("Calculated end date: {} years from {}", years, startDate);
                return startDate.plusYears(years);
            } catch (Exception e) {
                log.warn("Cannot parse term: {}. Using default 1 year", term);
                return startDate.plusYears(1);
            }
        } else if (normalizedTerm.contains("month")) {
            // Support English format (VD: "1 month", "6 months")
            try {
                String[] parts = normalizedTerm.split("\\s+");
                int months = Integer.parseInt(parts[0]);
                log.info("Calculated end date: {} months from {}", months, startDate);
                return startDate.plusMonths(months);
            } catch (Exception e) {
                log.warn("Cannot parse term: {}. Using default 6 months", term);
                return startDate.plusMonths(6);
            }
        } else if (normalizedTerm.contains("year")) {
            // Support English format (VD: "1 year", "2 years")
            try {
                String[] parts = normalizedTerm.split("\\s+");
                int years = Integer.parseInt(parts[0]);
                log.info("Calculated end date: {} years from {}", years, startDate);
                return startDate.plusYears(years);
            } catch (Exception e) {
                log.warn("Cannot parse term: {}. Using default 1 year", term);
                return startDate.plusYears(1);
            }
        } else {
            // Format không nhận diện được, mặc định 1 năm
            log.warn("Unknown term format: {}. Using default 1 year", term);
            return startDate.plusYears(1);
        }
    }
}

