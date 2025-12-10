package com.swp391.clubmanagement.dto.response;

import com.swp391.clubmanagement.enums.RoleType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {
    String userId;
    String fullName;
    String email;
    String phoneNumber;
    String studentCode;
    RoleType role;
    boolean isActive;
}