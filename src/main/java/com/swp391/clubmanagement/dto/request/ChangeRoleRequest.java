package com.swp391.clubmanagement.dto.request;

import com.swp391.clubmanagement.enums.ClubRoleType;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChangeRoleRequest {
    
    @NotNull(message = "Vai trò mới không được để trống")
    ClubRoleType newRole; // ThanhVien, ThuKy, PhoChuTich, ChuTich
}
