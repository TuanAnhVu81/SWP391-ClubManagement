package com.swp391.clubmanagement.dto.request;

import com.swp391.clubmanagement.enums.JoinStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ApproveRegisterRequest {
    Integer subscriptionId;
    JoinStatus status; // DaDuyet hoặc TuChoi
}

