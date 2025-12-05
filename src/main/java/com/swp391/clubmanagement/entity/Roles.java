package com.swp391.clubmanagement.entity;

import com.swp391.clubmanagement.enums.RoleType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "Roles")
public class Roles {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    Integer roleId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "role_name", nullable = false, unique = true)
    RoleType roleName;
}

