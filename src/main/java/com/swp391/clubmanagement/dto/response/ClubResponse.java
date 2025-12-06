package com.swp391.clubmanagement.dto.response;

import com.swp391.clubmanagement.enums.ClubCategory;
import lombok.*;
import lombok.experimental.FieldDefaults;

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
    Boolean isActive;
    LocalDate establishedDate;
    
    // Thông tin founder
    String founderId;
    String founderName;
    String founderStudentCode;
}
