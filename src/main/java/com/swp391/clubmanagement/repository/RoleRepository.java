package com.swp391.clubmanagement.repository;

import com.swp391.clubmanagement.entity.Roles;
import com.swp391.clubmanagement.enums.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * RoleRepository - Spring Data JPA Repository cho entity Roles
 * 
 * Interface này kế thừa JpaRepository, cung cấp sẵn các method CRUD cơ bản.
 * Sử dụng để quản lý các vai trò (Role) trong hệ thống: QuanTriVien, SinhVien, ChuTich
 */
@Repository
public interface RoleRepository extends JpaRepository<Roles, Integer> {
    /**
     * Tìm role theo tên vai trò (RoleType enum)
     * 
     * @param roleName Tên vai trò cần tìm (QuanTriVien, SinhVien, ChuTich)
     * @return Optional<Roles> - có thể rỗng nếu không tìm thấy
     */
    Optional<Roles> findByRoleName(RoleType roleName);
}

