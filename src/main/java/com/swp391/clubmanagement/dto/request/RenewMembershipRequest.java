package com.swp391.clubmanagement.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Request DTO cho API gia hạn membership
 * packageId optional: null = gia hạn gói hiện tại, có giá trị = đổi sang gói mới
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RenewMembershipRequest {
    /**
     * ID của gói membership mới (optional)
     * - Nếu null hoặc bỏ trống: Gia hạn gói hiện tại
     * - Nếu có giá trị: Đổi sang gói mới (nâng cấp/hạ cấp)
     */
    Integer packageId;
}

