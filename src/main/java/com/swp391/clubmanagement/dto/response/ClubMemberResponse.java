package com.swp391.clubmanagement.dto.response;

import com.swp391.clubmanagement.enums.JoinStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubMemberResponse {
    
    String userId;
    String studentCode;
    String fullName;
    String major;
    String phoneNumber;
    String email;
    String avatarUrl;
    
    // Thông tin membership
    String packageName;
    JoinStatus status;
    LocalDateTime joinedAt;
}
