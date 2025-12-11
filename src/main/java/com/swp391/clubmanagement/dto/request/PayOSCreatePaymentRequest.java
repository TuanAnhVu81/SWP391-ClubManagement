package com.swp391.clubmanagement.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PayOSCreatePaymentRequest {
    @JsonProperty("orderCode")
    Long orderCode;  // PayOS yêu cầu orderCode là số nguyên dương, tối đa 19 chữ số
    
    @JsonProperty("amount")
    Integer amount;
    
    @JsonProperty("description")
    String description;
    
    @JsonProperty("items")
    List<ItemData> items; // Danh sách sản phẩm (required by PayOS)
    
    @JsonProperty("returnUrl")
    String returnUrl;
    
    @JsonProperty("cancelUrl")
    String cancelUrl;

    @JsonProperty("signature")
    String signature;

    @JsonProperty("expiredAt")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Long expiredAt;
    
    @JsonProperty("buyerName")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String buyerName;
    
    @JsonProperty("buyerEmail")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String buyerEmail;
    
    @JsonProperty("buyerPhone")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String buyerPhone;
    
    @JsonProperty("buyerAddress")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String buyerAddress;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ItemData {
        @JsonProperty("name")
        String name;        // Tên sản phẩm
        
        @JsonProperty("quantity")
        Integer quantity;   // Số lượng
        
        @JsonProperty("price")
        Integer price;      // Giá mỗi đơn vị

    }
}

