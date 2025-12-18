package com.swp391.clubmanagement.mapper;

import com.swp391.clubmanagement.dto.response.ClubMemberResponse;
import com.swp391.clubmanagement.dto.response.ClubResponse;
import com.swp391.clubmanagement.entity.Clubs;
import com.swp391.clubmanagement.entity.Registers;
import com.swp391.clubmanagement.enums.ClubRoleType;
import org.mapstruct.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface ClubMapper {
    
    Logger log = LoggerFactory.getLogger(ClubMapper.class);
    
    @Mapping(source = "founder.userId", target = "founderId")
    @Mapping(source = "founder.fullName", target = "founderName")
    @Mapping(source = "founder.studentCode", target = "founderStudentCode")
    ClubResponse toResponse(Clubs club);
    
    @Mapping(source = "user.userId", target = "userId")
    @Mapping(source = "user.studentCode", target = "studentCode")
    @Mapping(source = "user.fullName", target = "fullName")
    @Mapping(source = "user.major", target = "major")
    @Mapping(source = "user.phoneNumber", target = "phoneNumber")
    @Mapping(source = "user.email", target = "email")
    @Mapping(source = "user.avatarUrl", target = "avatarUrl")
    @Mapping(source = "membershipPackage.packageName", target = "packageName")
    @Mapping(source = "clubRole", target = "clubRole")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "joinDate", target = "joinedAt")
    @Mapping(source = "endDate", target = "endDate")
    ClubMemberResponse toMemberResponseInternal(Registers register);
    
    /**
     * Wrapper method để giữ nguyên logic null check từ class cũ
     * Nếu register hoặc register.getUser() là null thì return null
     */
    default ClubMemberResponse toMemberResponse(Registers register) {
        if (register == null || register.getUser() == null) {
            return null;
        }
        ClubMemberResponse response = toMemberResponseInternal(register);
        
        // Debug logging (giữ nguyên logic từ class cũ)
        if (response != null) {
            ClubRoleType clubRole = register.getClubRole();
            log.debug("Mapping register {}: userId={}, clubRole={}", 
                    register.getSubscriptionId(), register.getUser().getUserId(), clubRole);
        }
        
        return response;
    }
}
