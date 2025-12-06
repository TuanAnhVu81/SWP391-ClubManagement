package com.swp391.clubmanagement.dto.response;

import com.swp391.clubmanagement.enums.ClubCategory;
import com.swp391.clubmanagement.enums.RequestStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubApplicationResponse {
    
    Integer requestId;
    String proposedName;
    ClubCategory category;
    String purpose;
    RequestStatus status;
    String adminNote;
    
    // Thông tin người tạo đơn
    Integer creatorId;
    String creatorName;
    String creatorStudentCode;
    
    // Thông tin reviewer (nếu có)
    Integer reviewerId;
    String reviewerName;
    
    // Club ID nếu đã được duyệt
    Integer clubId;
    
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
