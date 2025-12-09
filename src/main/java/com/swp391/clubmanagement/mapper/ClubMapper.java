package com.swp391.clubmanagement.mapper;

import com.swp391.clubmanagement.dto.response.ClubMemberResponse;
import com.swp391.clubmanagement.dto.response.ClubResponse;
import com.swp391.clubmanagement.entity.Clubs;
import com.swp391.clubmanagement.entity.Registers;
import com.swp391.clubmanagement.enums.ClubRoleType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
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
                .email(club.getEmail())
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
        
        ClubRoleType clubRole = register.getClubRole();
        log.debug("Mapping register {}: userId={}, clubRole={}", 
                register.getSubscriptionId(), register.getUser().getUserId(), clubRole);
        
        return ClubMemberResponse.builder()
                .userId(register.getUser().getUserId())
                .studentCode(register.getUser().getStudentCode())
                .fullName(register.getUser().getFullName())
                .major(register.getUser().getMajor())
                .phoneNumber(register.getUser().getPhoneNumber())
                .email(register.getUser().getEmail())
                .avatarUrl(register.getUser().getAvatarUrl())
                .packageName(register.getMembershipPackage() != null ? register.getMembershipPackage().getPackageName() : null)
                .clubRole(clubRole) // Vai trò trong CLB
                .status(register.getStatus())
                .joinedAt(register.getJoinDate())
                .endDate(register.getEndDate()) // Ngày hết hạn
                .build();
    }
}
