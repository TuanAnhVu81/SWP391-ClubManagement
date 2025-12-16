package com.swp391.clubmanagement.dto.response;

import com.swp391.clubmanagement.enums.ClubCategory;
import com.swp391.clubmanagement.enums.ClubRoleType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JoinedClubResponse {
    
    Integer clubId;
    String clubName;
    ClubCategory category;
    String logo;
    String location;
    String description;
    String email;
    Boolean isActive;
    LocalDate establishedDate;
    
    // Thông tin founder
    String founderId;
    String founderName;
    String founderStudentCode;
    
    // Thông tin thành viên của user trong CLB này
    Integer subscriptionId; // ID của đăng ký (để gọi API gia hạn)
    Integer packageId; // ID của gói membership
    String packageName; // Tên gói membership
    ClubRoleType clubRole; // Vai trò của user trong CLB
    LocalDateTime joinedAt; // Ngày tham gia
    LocalDateTime endDate; // Ngày hết hạn membership
    
    // Thông tin gia hạn (cho FE hiển thị nút gia hạn)
    Boolean canRenew; // true nếu status = HetHan, false nếu còn hạn
    Boolean isExpired; // true nếu endDate < now
}

