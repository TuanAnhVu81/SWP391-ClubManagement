package com.swp391.clubmanagement.mapper;

import com.swp391.clubmanagement.dto.request.UserCreationRequest;
import com.swp391.clubmanagement.dto.request.UserUpdateRequest;
import com.swp391.clubmanagement.dto.response.UserResponse;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.enums.RoleType;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface UserMapper {
    Users toUser(UserCreationRequest request);

    @Mapping(target = "role", expression = "java(user.getRole() != null ? user.getRole().getRoleName() : null)")
    @Mapping(target = "isActive", source = "enabled")
    UserResponse toUserResponse(Users user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateUser(@MappingTarget Users user, UserUpdateRequest request);

}

