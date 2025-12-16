package com.swp391.clubmanagement.dto.response;

import com.swp391.clubmanagement.enums.ClubRoleType;
import com.swp391.clubmanagement.enums.JoinStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegisterResponse {
    Integer subscriptionId;
    
    // Thông tin sinh viên đăng ký
    String userId;
    String studentCode;
    String studentName;
    String studentEmail;
    
    // Thông tin CLB
    Integer clubId;
    String clubName;
    String clubLogo;
    
    // Thông tin gói
    Integer packageId;
    String packageName;
    String term;
    BigDecimal price;
    
    // Trạng thái
    JoinStatus status;
    String joinReason; // Lý do gia nhập CLB
    Boolean isPaid;
    String paymentMethod;
    ClubRoleType clubRole;
    
    // Thông tin người duyệt
    String approverName;
    
    // Thời gian
    LocalDateTime createdAt;
    LocalDateTime paymentDate;
    LocalDateTime startDate;
    LocalDateTime endDate;
    LocalDateTime joinDate;
    
    // Thông tin gia hạn (cho FE hiển thị nút gia hạn)
    Boolean canRenew; // true nếu status = HetHan, false nếu còn hạn hoặc không phù hợp
    Boolean isExpired; // true nếu endDate < now
}

