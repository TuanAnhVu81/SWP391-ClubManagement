package com.swp391.clubmanagement.mapper;

import com.swp391.clubmanagement.dto.request.UserCreationRequest;
import com.swp391.clubmanagement.dto.request.UserUpdateRequest;
import com.swp391.clubmanagement.dto.response.UserResponse;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.enums.JoinStatus;
import org.mapstruct.*;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserMapper {
    Users toUser(UserCreationRequest request);

    @Mapping(target = "role", expression = "java(user.getRole() != null ? user.getRole().getRoleName() : null)")
    @Mapping(target = "isActive", source = "isActive")
    @Mapping(target = "major", source = "major")
    @Mapping(target = "clubIds", ignore = true)
    UserResponse toUserResponse(Users user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateUser(@MappingTarget Users user, UserUpdateRequest request);

    /**
     * AfterMapping: Thêm logic custom để map clubIds
     * Lấy danh sách ID các CLB mà user đang là thành viên chính thức (DaDuyet + isPaid)
     */
    @AfterMapping
    default void mapClubIds(@MappingTarget UserResponse response, Users user) {
        if (user.getRegisters() != null && !user.getRegisters().isEmpty()) {
            List<Integer> clubIds = user.getRegisters().stream()
                    .filter(r -> r != null && 
                            r.getStatus() == JoinStatus.DaDuyet && 
                            r.getIsPaid() != null && r.getIsPaid() &&
                            r.getMembershipPackage() != null &&
                            r.getMembershipPackage().getClub() != null)
                    .map(r -> r.getMembershipPackage().getClub().getClubId())
                    .distinct()
                    .collect(Collectors.toList());
            response.setClubIds(clubIds.isEmpty() ? null : clubIds);
        } else {
            response.setClubIds(null);
        }
    }
}

