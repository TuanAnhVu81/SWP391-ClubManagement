package com.swp391.clubmanagement.dto.response;

import com.swp391.clubmanagement.enums.RoleType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

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
    String major;
    RoleType role;
    boolean isActive;
    LocalDateTime createdAt;
    
    // Danh sách ID các CLB mà user đang là thành viên chính thức
    List<Integer> clubIds;
}