package com.swp391.clubmanagement.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MembershipResponse {
    Integer packageId;
    Integer clubId;
    String clubName;
    String packageName;
    String term;
    BigDecimal price;
    String description;
    Boolean isActive;
    LocalDateTime createdAt;
}

