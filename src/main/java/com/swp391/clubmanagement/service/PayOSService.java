package com.swp391.clubmanagement.service;

import com.swp391.clubmanagement.dto.request.PayOSCreatePaymentRequest;
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

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PayOSService {
    
    RestTemplate restTemplate = new RestTemplate();
    
    @NonFinal
    @Value("${payos.client-id}")
    String clientId;
    
    @NonFinal
    @Value("${payos.api-key}")
    String apiKey;
    
    @NonFinal
    @Value("${payos.checksum-key}")
    String checksumKey;
    
    @NonFinal
    @Value("${payos.api-url}")
    String apiUrl;
    
    @NonFinal
    @Value("${app.base-url}")
    String baseUrl;

    /**
     * Tạo payment link từ PayOS
     */
    public PayOSPaymentLinkResponse createPaymentLink(PayOSCreatePaymentRequest request) {
        try {
            String url = apiUrl + "/v2/payment-requests";
            
            log.info("Creating payment link: orderCode={}, amount={}, description={}, returnUrl={}, cancelUrl={}, expiredAt={}", 
                    request.getOrderCode(), request.getAmount(), request.getDescription(), 
                    request.getReturnUrl(), request.getCancelUrl(), request.getExpiredAt());
            
            // Validate request
            if (request.getOrderCode() == null || request.getOrderCode() <= 0) {
                log.error("Invalid orderCode: {}", request.getOrderCode());
                throw new AppException(ErrorCode.INVALID_REQUEST, "orderCode must be a positive integer");
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
                
                // Kiểm tra response có code = "00" (thành công)
                if (body.getCode() == null || !"00".equals(body.getCode())) {
                    log.error("PayOS API returned error: code={}, desc={}", body.getCode(), body.getDesc());
                    throw new AppException(ErrorCode.PAYMENT_LINK_CREATION_FAILED);
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
                        response.getBody() != null ? "present" : "null");
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
     */
    public boolean verifyWebhookSignature(String code, String desc, PayOSWebhookData.WebhookData data, String signature) {
        try {
            if (data == null || signature == null || signature.isEmpty()) {
                return false;
            }
            
            // PayOS webhook signature được tạo từ các trường trong data
            // Format: amount + orderCode + description
            String dataStr = String.format(
                    "amount=%d&description=%s&orderCode=%d",
                    data.getAmount(),
                    data.getDescription() != null ? data.getDescription() : "",
                    data.getOrderCode()
            );
            
            // Tạo signature bằng HMAC SHA256
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(checksumKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(dataStr.getBytes(StandardCharsets.UTF_8));
            String calculatedSignature = bytesToHex(hash);
            
            return calculatedSignature.equalsIgnoreCase(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error verifying webhook signature", e);
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
}

