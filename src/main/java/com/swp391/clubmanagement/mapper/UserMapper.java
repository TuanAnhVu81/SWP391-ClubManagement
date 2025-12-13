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
    @Mapping(target = "clubIds", expression = "java(extractClubIds(user))")
    UserResponse toUserResponse(Users user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateUser(@MappingTarget Users user, UserUpdateRequest request);

    /**
     * Extract clubIds từ user's registers
     * Lấy danh sách ID các CLB mà user đang là thành viên chính thức (DaDuyet + isPaid)
     */
    default List<Integer> extractClubIds(Users user) {
        if (user == null || user.getRegisters() == null || user.getRegisters().isEmpty()) {
            return null;
        }
        
        List<Integer> clubIds = user.getRegisters().stream()
                .filter(r -> r != null && 
                        r.getStatus() == JoinStatus.DaDuyet && 
                        r.getIsPaid() != null && r.getIsPaid() &&
                        r.getMembershipPackage() != null &&
                        r.getMembershipPackage().getClub() != null)
                .map(r -> r.getMembershipPackage().getClub().getClubId())
                .distinct()
                .collect(Collectors.toList());
        
        return clubIds.isEmpty() ? null : clubIds;
    }
}

