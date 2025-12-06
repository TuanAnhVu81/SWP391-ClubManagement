package com.swp391.clubmanagement.mapper;

import com.swp391.clubmanagement.dto.response.ClubMemberResponse;
import com.swp391.clubmanagement.dto.response.ClubResponse;
import com.swp391.clubmanagement.entity.Clubs;
import com.swp391.clubmanagement.entity.Registers;
import org.springframework.stereotype.Component;

@Component
public class ClubMapper {
    
    public ClubResponse toResponse(Clubs club) {
        if (club == null) {
            return null;
        }
        
        return ClubResponse.builder()
                .clubId(club.getClubId())
                .clubName(club.getClubName())
                .category(club.getCategory())
                .logo(club.getLogo())
                .location(club.getLocation())
                .description(club.getDescription())
                .isActive(club.getIsActive())
                .establishedDate(club.getEstablishedDate())
                .founderId(club.getFounder() != null ? club.getFounder().getUserId() : null)
                .founderName(club.getFounder() != null ? club.getFounder().getFullName() : null)
                .founderStudentCode(club.getFounder() != null ? club.getFounder().getStudentCode() : null)
                .build();
    }
    
    public ClubMemberResponse toMemberResponse(Registers register) {
        if (register == null || register.getUser() == null) {
            return null;
        }
        
        return ClubMemberResponse.builder()
                .userId(register.getUser().getUserId())
                .studentCode(register.getUser().getStudentCode())
                .fullName(register.getUser().getFullName())
                .major(register.getUser().getMajor())
                .phoneNumber(register.getUser().getPhoneNumber())
                .email(register.getUser().getEmail())
                .avatarUrl(register.getUser().getAvatarUrl())
                .packageName(register.getMembershipPackage() != null ? register.getMembershipPackage().getPackageName() : null)
                .status(register.getStatus())
                .joinedAt(register.getJoinDate())
                .build();
    }
}
