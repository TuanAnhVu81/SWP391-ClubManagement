package com.swp391.clubmanagement.mapper;

import com.swp391.clubmanagement.dto.response.MembershipResponse;
import com.swp391.clubmanagement.entity.Memberships;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MembershipMapper {
    
    @Mapping(source = "club.clubId", target = "clubId")
    @Mapping(source = "club.clubName", target = "clubName")
    MembershipResponse toMembershipResponse(Memberships membership);
    
    List<MembershipResponse> toMembershipResponseList(List<Memberships> memberships);
}

