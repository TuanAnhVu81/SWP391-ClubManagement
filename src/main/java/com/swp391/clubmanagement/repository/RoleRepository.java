package com.swp391.clubmanagement.repository;

import com.swp391.clubmanagement.entity.Roles;
import com.swp391.clubmanagement.enums.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Roles, Integer> {
    Optional<Roles> findByRoleName(RoleType roleName);
}

