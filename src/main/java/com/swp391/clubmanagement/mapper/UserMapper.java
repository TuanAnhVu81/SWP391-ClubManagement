package com.swp391.clubmanagement.mapper;

import com.swp391.clubmanagement.dto.request.UserCreationRequest;
import com.swp391.clubmanagement.dto.request.UserUpdateRequest;
import com.swp391.clubmanagement.entity.Users;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface UserMapper {
    Users toUser(UserCreationRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateUser(@MappingTarget Users user, UserUpdateRequest request);
}

