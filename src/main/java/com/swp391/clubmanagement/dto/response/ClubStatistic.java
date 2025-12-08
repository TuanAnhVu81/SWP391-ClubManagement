package com.swp391.clubmanagement.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubStatistic {
    Integer clubId;
    String clubName;
    String clubLogo;
    String category;
    Long memberCount;
}

