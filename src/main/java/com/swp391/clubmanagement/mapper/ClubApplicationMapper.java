package com.swp391.clubmanagement.mapper;

import com.swp391.clubmanagement.dto.response.ClubApplicationResponse;
import com.swp391.clubmanagement.entity.ClubApplications;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface ClubApplicationMapper {
    
    @Mapping(source = "creator.userId", target = "creatorId")
    @Mapping(source = "creator.fullName", target = "creatorName")
    @Mapping(source = "creator.studentCode", target = "creatorStudentCode")
    @Mapping(source = "reviewer.userId", target = "reviewerId")
    @Mapping(source = "reviewer.fullName", target = "reviewerName")
    @Mapping(source = "club.clubId", target = "clubId")
    ClubApplicationResponse toResponse(ClubApplications application);
}
