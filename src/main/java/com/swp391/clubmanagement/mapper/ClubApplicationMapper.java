package com.swp391.clubmanagement.mapper;

import com.swp391.clubmanagement.dto.response.ClubApplicationResponse;
import com.swp391.clubmanagement.entity.ClubApplications;
import org.springframework.stereotype.Component;

@Component
public class ClubApplicationMapper {
    
    public ClubApplicationResponse toResponse(ClubApplications application) {
        if (application == null) {
            return null;
        }
        
        return ClubApplicationResponse.builder()
                .requestId(application.getRequestId())
                .proposedName(application.getProposedName())
                .category(application.getCategory())
                .purpose(application.getPurpose())
                .status(application.getStatus())
                .adminNote(application.getAdminNote())
                .creatorId(application.getCreator().getUserId())
                .creatorName(application.getCreator().getFullName())
                .creatorStudentCode(application.getCreator().getStudentCode())
                .reviewerId(application.getReviewer() != null ? application.getReviewer().getUserId() : null)
                .reviewerName(application.getReviewer() != null ? application.getReviewer().getFullName() : null)
                .clubId(application.getClub() != null ? application.getClub().getClubId() : null)
                .createdAt(application.getCreatedAt())
                .updatedAt(application.getUpdatedAt())
                .build();
    }
}
