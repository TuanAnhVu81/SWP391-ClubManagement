// Package định nghĩa service layer - xử lý tích hợp với PayOS payment gateway
package com.swp391.clubmanagement.service;

// ========== DTO ==========
import com.swp391.clubmanagement.dto.request.PayOSCreatePaymentRequest; // Request tạo payment link
import com.swp391.clubmanagement.dto.response.ConfirmWebhookResponse; // Response xác nhận webhook
import com.swp391.clubmanagement.dto.response.PayOSPaymentLinkResponse; // Response payment link
import com.swp391.clubmanagement.dto.response.PayOSWebhookData; // Dữ liệu webhook từ PayOS

// ========== Exception ==========
import com.swp391.clubmanagement.exception.AppException; // Custom exception
import com.swp391.clubmanagement.exception.ErrorCode; // Mã lỗi hệ thống

// ========== Lombok ==========
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor; // Tự động tạo constructor inject dependencies
import lombok.experimental.FieldDefaults; // Tự động thêm private final cho fields
import lombok.experimental.NonFinal; // Cho phép field không final
import lombok.extern.slf4j.Slf4j; // Tự động tạo logger

// ========== Spring Framework ==========
import org.springframework.beans.factory.annotation.Value; // Inject giá trị từ config
import org.springframework.http.*; // HTTP headers, status, media type
import org.springframework.stereotype.Service; // Đánh dấu class là Spring Service Bean
import org.springframework.web.client.HttpClientErrorException; // Exception cho 4xx errors
import org.springframework.web.client.HttpServerErrorException; // Exception cho 5xx errors
import org.springframework.web.client.RestClientException; // Exception cho network errors
import org.springframework.web.client.RestTemplate; // HTTP client để gọi PayOS API

// ========== Java Cryptography ==========
import javax.crypto.Mac; // HMAC (Hash-based Message Authentication Code)
import javax.crypto.spec.SecretKeySpec; // Secret key cho HMAC
import java.nio.charset.StandardCharsets; // UTF-8 charset
import java.security.InvalidKeyException; // Exception khi key không hợp lệ
import java.security.NoSuchAlgorithmException; // Exception khi algorithm không tồn tại

/**
 * Service tích hợp với PayOS payment gateway
 * 
 * Chức năng chính:
 * - Tạo payment link từ PayOS (để user thanh toán)
 * - Xác thực webhook signature từ PayOS (đảm bảo webhook hợp lệ)
 * - Xác nhận webhook URL với PayOS
 * 
 * Business Rules:
 * - Sử dụng HMAC SHA256 để tạo và verify signature
 * - Signature được tạo từ: amount + orderCode + description
 * - Tất cả request đến PayOS API đều cần client-id và api-key
 * 
 * @Service: Spring Service Bean, được quản lý bởi IoC Container
 * @RequiredArgsConstructor: Lombok tự động tạo constructor inject dependencies
 * @FieldDefaults: Tự động thêm private final cho các field
 * @Slf4j: Tự động tạo logger với tên "log"
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PayOSService {
    
    /** RestTemplate để gọi PayOS API (HTTP client) */
    RestTemplate restTemplate = new RestTemplate();
    
    /** Client ID của PayOS (đọc từ application.properties) */
    @NonFinal
    @Value("${payos.client-id}")
    String clientId;
    
    /** API Key của PayOS (đọc từ application.properties) */
    @NonFinal
    @Value("${payos.api-key}")
    String apiKey;
    
    /** Checksum Key để tạo và verify signature (đọc từ application.properties) */
    @NonFinal
    @Value("${payos.checksum-key}")
    String checksumKey;
    
    /** Base URL của PayOS API (đọc từ application.properties) */
    @NonFinal
    @Value("${payos.api-url}")
    String apiUrl;
    
    /** Base URL của ứng dụng (để tạo returnUrl và cancelUrl) */
    @NonFinal
    @Value("${app.base-url}")
    String baseUrl;

    /**
     * Tạo payment link từ PayOS
     */
    public PayOSPaymentLinkResponse createPaymentLink(PayOSCreatePaymentRequest request) {
        try {
            String url = apiUrl + "/v2/payment-requests";

            String dataStr = String.format(
                    "amount=%d&cancelUrl=%s&description=%s&orderCode=%d&returnUrl=%s",
                    request.getAmount(),
                    request.getCancelUrl(),
                    request.getDescription(),
                    request.getOrderCode(),
                    request.getReturnUrl()
            );

            String signature = createSignature(dataStr);
            request.setSignature(signature);

            log.info("Creating payment link with signature: {}", signature);

            log.info("Creating payment link: orderCode={}, amount={}, description={}, returnUrl={}, cancelUrl={}, expiredAt={}", 
                    request.getOrderCode(), request.getAmount(), request.getDescription(), 
                    request.getReturnUrl(), request.getCancelUrl(), request.getExpiredAt());
            
            // Validate request
            if (request.getOrderCode() == null || request.getOrderCode() <= 0L) {
                log.error("Invalid orderCode: {}", request.getOrderCode());
                throw new AppException(ErrorCode.INVALID_REQUEST, "orderCode must be a positive number");
            }
            if (request.getAmount() == null || request.getAmount() <= 0) {
                log.error("Invalid amount: {}", request.getAmount());
                throw new AppException(ErrorCode.INVALID_REQUEST, "amount must be a positive integer");
            }
            if (request.getDescription() == null || request.getDescription().isEmpty()) {
                log.error("Invalid description: {}", request.getDescription());
                throw new AppException(ErrorCode.INVALID_REQUEST, "description is required");
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-client-id", clientId);
            headers.set("x-api-key", apiKey);
            
            HttpEntity<PayOSCreatePaymentRequest> entity = new HttpEntity<>(request, headers);
            
            // Log request body for debugging (dùng ObjectMapper để serialize thành JSON)
            try {
                com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                String requestJson = objectMapper.writeValueAsString(request);
                log.info("PayOS API Request URL: {}", url);
                log.info("PayOS API Request Body (JSON): {}", requestJson);
                log.info("PayOS API Request Headers: x-client-id={}", clientId);
            } catch (Exception e) {
                log.warn("Failed to serialize request body: {}", e.getMessage());
            }
            
            ResponseEntity<PayOSPaymentLinkResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    PayOSPaymentLinkResponse.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                PayOSPaymentLinkResponse body = response.getBody();
                
                log.info("PayOS API Response: code={}, desc={}, data={}", 
                        body.getCode(), body.getDesc(), body.getData() != null ? "present" : "null");
                
                // Kiểm tra response có code = "00" (thành công)
                if (body.getCode() == null || !"00".equals(body.getCode())) {
                    log.error("PayOS API returned error: code={}, desc={}, full response={}", 
                            body.getCode(), body.getDesc(), body);
                    throw new AppException(ErrorCode.PAYMENT_LINK_CREATION_FAILED, 
                            "PayOS error: " + body.getDesc());
                }
                
                // PayOS không gửi signature trong payment link creation response
                // Signature chỉ có trong webhook callback, nên ta không verify signature ở đây
                log.info("Payment link created successfully: orderCode={}, paymentLinkId={}", 
                        body.getData() != null ? body.getData().getOrderCode() : "N/A",
                        body.getData() != null ? body.getData().getPaymentLinkId() : "N/A");
                
                return body;
            } else {
                log.error("Failed to create payment link: status={}, body={}", 
                        response.getStatusCode(), 
                        response.getBody());
                throw new AppException(ErrorCode.PAYMENT_LINK_CREATION_FAILED);
            }
        } catch (HttpClientErrorException e) {
            // 4xx errors (Bad Request, Unauthorized, etc.)
            log.error("PayOS API client error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new AppException(ErrorCode.PAYMENT_LINK_CREATION_FAILED, 
                    "PayOS API error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
        } catch (HttpServerErrorException e) {
            // 5xx errors (Internal Server Error, etc.)
            log.error("PayOS API server error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new AppException(ErrorCode.PAYMENT_LINK_CREATION_FAILED, 
                    "PayOS server error: " + e.getStatusCode());
        } catch (RestClientException e) {
            // Network errors, connection issues
            log.error("PayOS API connection error: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.PAYMENT_LINK_CREATION_FAILED, 
                    "Cannot connect to PayOS API: " + e.getMessage());
        } catch (AppException e) {
            // Re-throw AppException as-is
            throw e;
        } catch (Exception e) {
            // Any other unexpected errors
            log.error("Unexpected error creating payment link", e);
            throw new AppException(ErrorCode.PAYMENT_LINK_CREATION_FAILED, 
                    "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Xác thực webhook từ PayOS (wrapper method)
     */
    public boolean verifyWebhookSignature(PayOSWebhookData webhookData) {
        if (webhookData == null || webhookData.getData() == null) {
            return false;
        }
        return verifyWebhookSignature(
                webhookData.getCode(),
                webhookData.getDesc(),
                webhookData.getData(),
                webhookData.getSignature()
        );
    }


    /**
     * Verify webhook signature
     * PayOS webhook signature được tạo từ: amount, orderCode, description
     * Theo tài liệu PayOS, signature được tính từ data object
     */
    public boolean verifyWebhookSignature(String code, String desc, PayOSWebhookData.WebhookData data, String signature) {
        try {
            if (data == null || signature == null || signature.isEmpty()) {
                log.warn("Webhook signature verification failed: data or signature is null");
                return false;
            }
            
            // PayOS webhook signature được tạo từ các trường trong data
            // Format theo PayOS: amount + orderCode + description (theo thứ tự)
            // URL encode description nếu có ký tự đặc biệt
            String description = data.getDescription() != null ? data.getDescription() : "";
            
            // Tạo data string để verify signature
            // PayOS có thể dùng format: amount + orderCode + description
            String dataStr = String.format(
                    "amount=%d&description=%s&orderCode=%d",
                    data.getAmount() != null ? data.getAmount() : 0,
                    description,
                    data.getOrderCode() != null ? data.getOrderCode() : 0L
            );
            
            log.debug("Verifying webhook signature with data: {}", dataStr);
            
            // Tạo signature bằng HMAC SHA256
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(checksumKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(dataStr.getBytes(StandardCharsets.UTF_8));
            String calculatedSignature = bytesToHex(hash);
            
            boolean isValid = calculatedSignature.equalsIgnoreCase(signature);
            
            if (!isValid) {
                log.warn("Webhook signature mismatch. Expected: {}, Received: {}", calculatedSignature, signature);
            }
            
            return isValid;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error verifying webhook signature", e);
            return false;
        } catch (Exception e) {
            log.error("Unexpected error verifying webhook signature", e);
            return false;
        }
    }

    /**
     * Convert bytes to hex string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private String createSignature(String dataStr) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(checksumKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(dataStr.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // Log lỗi để debug
            log.error("Error creating signature: {}", e.getMessage());
            // Ném RuntimeException để code biên dịch được
            throw new RuntimeException("Cannot create signature: " + e.getMessage(), e);
        }
    }

    /**
     * Confirm webhook URL với PayOS
     * PayOS sẽ gửi GET request đến webhookUrl để verify
     */
    public ConfirmWebhookResponse confirmWebhook(String webhookUrl) {
        try {
            String url = apiUrl + "/confirm-webhook";
            
            log.info("Confirming webhook URL with PayOS: {}", webhookUrl);
            
            // Tạo request body
            java.util.Map<String, String> requestBody = new java.util.HashMap<>();
            requestBody.put("webhookUrl", webhookUrl);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-client-id", clientId);
            headers.set("x-api-key", apiKey);
            
            HttpEntity<java.util.Map<String, String>> entity = new HttpEntity<>(requestBody, headers);
            
            log.info("PayOS Confirm Webhook Request URL: {}", url);
            log.info("PayOS Confirm Webhook Request Body: {}", requestBody);
            
            ResponseEntity<ConfirmWebhookResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ConfirmWebhookResponse.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                ConfirmWebhookResponse body = response.getBody();
                
                log.info("PayOS Confirm Webhook Response: code={}, desc={}, data={}", 
                        body.getCode(), body.getDescription(), body.getData());
                
                // Kiểm tra response có code = "00" (thành công)
                if (body.getCode() == null || !"00".equals(body.getCode())) {
                    log.error("PayOS confirm webhook failed: code={}, desc={}", 
                            body.getCode(), body.getDescription());
                    throw new AppException(ErrorCode.INVALID_REQUEST, 
                            "PayOS confirm webhook failed: " + body.getDescription());
                }
                
                log.info("✅ Webhook confirmed successfully: {}", webhookUrl);
                return body;
            } else {
                log.error("Failed to confirm webhook: status={}, body={}", 
                        response.getStatusCode(), 
                        response.getBody());
                throw new AppException(ErrorCode.INVALID_REQUEST, "Failed to confirm webhook with PayOS");
            }
        } catch (HttpClientErrorException e) {
            log.error("PayOS confirm webhook client error: status={}, body={}", 
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new AppException(ErrorCode.INVALID_REQUEST, 
                    "PayOS confirm webhook error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
        } catch (HttpServerErrorException e) {
            log.error("PayOS confirm webhook server error: status={}, body={}", 
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new AppException(ErrorCode.INVALID_REQUEST, 
                    "PayOS server error: " + e.getStatusCode());
        } catch (RestClientException e) {
            log.error("PayOS confirm webhook connection error: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.INVALID_REQUEST, 
                    "Cannot connect to PayOS API: " + e.getMessage());
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error confirming webhook", e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, 
                    "Unexpected error: " + e.getMessage());
        }
    }
}

