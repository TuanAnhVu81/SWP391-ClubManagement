package com.swp391.clubmanagement.service;

import com.swp391.clubmanagement.dto.request.PayOSCreatePaymentRequest;
import com.swp391.clubmanagement.dto.response.ConfirmWebhookResponse;
import com.swp391.clubmanagement.dto.response.PayOSPaymentLinkResponse;
import com.swp391.clubmanagement.dto.response.PayOSWebhookData;
import com.swp391.clubmanagement.exception.AppException;
import com.swp391.clubmanagement.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * PayOSService - Service xử lý tích hợp với PayOS payment gateway
 * 
 * Service này chịu trách nhiệm tương tác với PayOS API để:
 * - Tạo payment link (link thanh toán) cho user
 * - Verify webhook signature để đảm bảo request đến từ PayOS
 * 
 * PayOS là một payment gateway phổ biến tại Việt Nam, cho phép thanh toán online
 * qua nhiều phương thức: thẻ ngân hàng, ví điện tử, QR code...
 * 
 * Quy trình tích hợp PayOS:
 * 1. Tạo payment link → PayOS trả về paymentLinkId và paymentUrl
 * 2. User click vào paymentUrl → Thanh toán trên PayOS
 * 3. PayOS gửi webhook về server → Verify signature → Xử lý thanh toán
 * 
 * Security:
 * - Sử dụng checksum key để verify webhook signature (đảm bảo request từ PayOS)
 * - Sử dụng client-id và api-key để authenticate với PayOS API
 * 
 * @Service - Đánh dấu đây là một Spring Service, được quản lý bởi Spring Container
 * @RequiredArgsConstructor - Lombok tự động tạo constructor với các field final để dependency injection
 * @FieldDefaults - Lombok: tất cả field là PRIVATE và FINAL (immutable dependencies)
 * @Slf4j - Lombok: tự động tạo logger với tên "log" để ghi log
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PayOSService {
    /**
     * RestTemplate để gọi PayOS REST API
     * Được khởi tạo mới, không dùng bean (có thể inject nếu cần config thêm)
     */
    RestTemplate restTemplate = new RestTemplate();
    
    /**
     * Client ID của PayOS account
     * Được inject từ application.yaml qua @Value annotation
     * 
     * @NonFinal - Field này không phải final vì cần có thể inject giá trị từ config
     */
    @NonFinal
    @Value("${payos.client-id}")
    String clientId;
    
    /**
     * API Key để authenticate với PayOS API
     * Được inject từ application.yaml qua @Value annotation
     * Phải giữ bí mật, không được commit vào git (nên dùng application-secret.yaml)
     * 
     * @NonFinal - Field này không phải final vì cần có thể inject giá trị từ config
     */
    @NonFinal
    @Value("${payos.api-key}")
    String apiKey;
    
    /**
     * Checksum Key để verify webhook signature
     * Được inject từ application.yaml qua @Value annotation
     * Dùng để verify các webhook request từ PayOS (đảm bảo request hợp lệ)
     * Phải giữ bí mật, không được commit vào git (nên dùng application-secret.yaml)
     * 
     * @NonFinal - Field này không phải final vì cần có thể inject giá trị từ config
     */
    @NonFinal
    @Value("${payos.checksum-key}")
    String checksumKey;
    
    /**
     * PayOS API base URL
     * Được inject từ application.yaml qua @Value annotation
     * Ví dụ: "https://api.payos.vn"
     * 
     * @NonFinal - Field này không phải final vì cần có thể inject giá trị từ config
     */
    @NonFinal
    @Value("${payos.api-url}")
    String apiUrl;
    
    /**
     * Base URL của ứng dụng (để tạo returnUrl và cancelUrl cho payment link)
     * Được inject từ application.yaml qua @Value annotation
     * Ví dụ: "https://clubmanage.azurewebsites.net/api"
     * 
     * @NonFinal - Field này không phải final vì cần có thể inject giá trị từ config
     */
    @NonFinal
    @Value("${app.base-url}")
    String baseUrl;

    /**
     * Tạo payment link từ PayOS API
     * 
     * Phương thức này gọi PayOS API để tạo một payment link.
     * Payment link này sẽ được gửi cho user để họ click vào và thanh toán.
     * 
     * Quy trình:
     * 1. Validate request (orderCode, amount, description phải hợp lệ)
     * 2. Tạo signature từ data string (để PayOS verify)
     * 3. Gọi PayOS API với headers (client-id, api-key) và body (request + signature)
     * 4. PayOS trả về paymentLinkId và paymentUrl
     * 5. Return response cho caller
     * 
     * Request validation:
     * - orderCode: Phải là số dương (positive number)
     * - amount: Phải là số dương (tính bằng VNĐ)
     * - description: Phải có giá trị (không null, không empty)
     * 
     * @param request - DTO chứa thông tin payment: orderCode, amount, description, returnUrl, cancelUrl
     * @return PayOSPaymentLinkResponse - Response từ PayOS chứa paymentLinkId và paymentUrl
     * @throws AppException với ErrorCode.INVALID_REQUEST nếu request không hợp lệ
     * @throws AppException với ErrorCode.PAYMENT_LINK_CREATION_FAILED nếu PayOS API trả về lỗi
     * 
     * Lưu ý:
     * - Signature được tạo từ data string và checksum key (HMAC SHA256)
     * - PayOS sẽ verify signature để đảm bảo request hợp lệ
     * - Response code "00" nghĩa là thành công, các code khác là lỗi
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
     * 
     * Phương thức này là wrapper method để verify webhook signature từ PayOSWebhookData object.
     * Extract các field từ object và gọi method verifyWebhookSignature() với parameters riêng lẻ.
     * 
     * @param webhookData - PayOSWebhookData object chứa code, desc, data, và signature
     * @return boolean - true nếu signature hợp lệ, false nếu không hợp lệ hoặc data null
     * 
     * Lưu ý: Method này chỉ là convenience wrapper, logic thực sự ở verifyWebhookSignature() với parameters riêng lẻ
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
     * Verify webhook signature từ PayOS
     * 
     * Phương thức này verify chữ ký (signature) của webhook request từ PayOS
     * để đảm bảo request thực sự đến từ PayOS và không bị giả mạo.
     * 
     * Quy trình verify:
     * 1. Tạo data string từ các field: amount, orderCode, description (format: "amount=X&description=Y&orderCode=Z")
     * 2. Tạo signature từ data string bằng HMAC SHA256 với checksumKey
     * 3. So sánh signature tính được với signature từ PayOS
     * 4. Nếu khớp → Request hợp lệ, return true
     * 5. Nếu không khớp → Request không hợp lệ (có thể bị giả mạo), return false
     * 
     * Security importance:
     * - Đây là bước bảo mật quan trọng, phải verify signature trước khi xử lý webhook
     * - Không verify signature có thể dẫn đến xử lý thanh toán giả mạo
     * - PayOS sẽ gửi signature trong header hoặc body của webhook request
     * 
     * @param code - Code từ PayOS webhook (thường là "00" nếu thành công)
     * @param desc - Description từ PayOS webhook
     * @param data - WebhookData object chứa amount, orderCode, description
     * @param signature - Signature từ PayOS để verify
     * @return boolean - true nếu signature hợp lệ (request từ PayOS), false nếu không hợp lệ
     * 
     * Lưu ý:
     * - Signature được tính bằng HMAC SHA256 với checksumKey
     * - So sánh case-insensitive (equalsIgnoreCase)
     * - Nếu data hoặc signature null/empty, return false ngay
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
     * Convert mảng bytes sang hex string
     * 
     * Helper method để chuyển đổi mảng bytes (từ HMAC hash) sang chuỗi hex string.
     * Được dùng trong createSignature() và verifyWebhookSignature().
     * 
     * Ví dụ: [0x48, 0x65, 0x6c] → "48656c"
     * 
     * @param bytes - Mảng bytes cần chuyển đổi (thường là kết quả từ HMAC hash)
     * @return String - Hex string representation của bytes array
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Tạo signature từ data string bằng HMAC SHA256
     * 
     * Helper method để tạo signature cho PayOS API request.
     * Signature được tạo bằng cách:
     * 1. Lấy data string (format: "amount=X&cancelUrl=Y&description=Z&orderCode=W&returnUrl=V")
     * 2. Hash bằng HMAC SHA256 với checksumKey
     * 3. Convert bytes sang hex string
     * 
     * Signature này được gửi kèm trong request để PayOS verify tính hợp lệ của request.
     * 
     * @param dataStr - Data string cần tạo signature (format URL-encoded query string)
     * @return String - Signature dạng hex string
     * @throws RuntimeException - Nếu có lỗi khi tạo signature (NoSuchAlgorithmException, InvalidKeyException)
     * 
     * Lưu ý:
     * - Sử dụng checksumKey (secret key) để tạo HMAC
     * - Algorithm: HMAC SHA256 (theo chuẩn PayOS)
     * - Kết quả là hex string (lowercase)
     */
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
     * 
     * Phương thức này đăng ký webhook URL với PayOS để PayOS biết gửi webhook đến đâu.
     * Sau khi confirm, PayOS sẽ gửi webhook đến URL này mỗi khi có sự kiện thanh toán.
     * 
     * Quy trình:
     * 1. Gọi PayOS API endpoint: /confirm-webhook
     * 2. Gửi webhookUrl cần đăng ký
     * 3. PayOS sẽ gửi GET request đến webhookUrl để verify URL có hoạt động không
     * 4. Nếu verify thành công, PayOS sẽ lưu webhookUrl và gửi webhook đến đây
     * 
     * Use cases:
     * - Đăng ký webhook URL khi deploy ứng dụng lần đầu
     * - Thay đổi webhook URL khi migrate server
     * - Re-confirm webhook URL nếu bị mất
     * 
     * @param webhookUrl - URL mà PayOS sẽ gửi webhook đến (ví dụ: "https://your-domain.com/api/payments/webhook")
     * @return ConfirmWebhookResponse - Response từ PayOS xác nhận đã đăng ký thành công
     * @throws AppException với ErrorCode.INVALID_REQUEST nếu PayOS trả về lỗi hoặc không thể confirm
     * 
     * Lưu ý:
     * - Webhook URL phải là public URL (không thể là localhost)
     * - PayOS sẽ gửi GET request đến URL này để verify
     * - Response code "00" nghĩa là thành công
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

