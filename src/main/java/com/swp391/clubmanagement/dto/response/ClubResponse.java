package com.swp391.clubmanagement.dto.response;

import com.swp391.clubmanagement.enums.ClubCategory;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubResponse {
    
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
    
    // Tổng số thành viên chính thức (đã duyệt và đã đóng phí)
    Long totalMembers;
}
