package com.swp391.clubmanagement.mapper;

import com.swp391.clubmanagement.dto.response.RegisterResponse;
import com.swp391.clubmanagement.entity.Registers;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RegisterMapper {
    
    @Mapping(source = "user.userId", target = "userId")
    @Mapping(source = "user.studentCode", target = "studentCode")
    @Mapping(source = "user.fullName", target = "studentName")
    @Mapping(source = "user.email", target = "studentEmail")
    @Mapping(source = "membershipPackage.club.clubId", target = "clubId")
    @Mapping(source = "membershipPackage.club.clubName", target = "clubName")
    @Mapping(source = "membershipPackage.club.logo", target = "clubLogo")
    @Mapping(source = "membershipPackage.packageId", target = "packageId")
    @Mapping(source = "membershipPackage.packageName", target = "packageName")
    @Mapping(source = "membershipPackage.term", target = "term")
    @Mapping(source = "membershipPackage.price", target = "price")
    @Mapping(source = "approver.fullName", target = "approverName")
    RegisterResponse toRegisterResponse(Registers registers);
    
    List<RegisterResponse> toRegisterResponseList(List<Registers> registersList);
}

