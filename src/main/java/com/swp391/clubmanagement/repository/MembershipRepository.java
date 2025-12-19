package com.swp391.clubmanagement.repository;

import com.swp391.clubmanagement.entity.Memberships;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MembershipRepository - Spring Data JPA Repository cho entity Memberships
 * 
 * Interface này kế thừa JpaRepository, cung cấp sẵn các method CRUD cơ bản.
 * Sử dụng để quản lý các gói membership (gói đăng ký) của các CLB.
 */
@Repository
public interface MembershipRepository extends JpaRepository<Memberships, Integer> {
    /**
     * Tìm tất cả các gói membership của một CLB (bao gồm cả active và inactive)
     * 
     * @param clubId ID của CLB cần tìm gói membership
     * @return Danh sách các gói membership của CLB đó
     */
    List<Memberships> findByClub_ClubId(Integer clubId);
    
    /**
     * Tìm các gói membership của CLB với trạng thái active/inactive cụ thể
     * 
     * @param clubId ID của CLB
     * @param isActive Trạng thái: true = gói đang bán, false = gói đã ngừng cung cấp
     * @return Danh sách các gói membership thỏa mãn điều kiện
     */
    List<Memberships> findByClub_ClubIdAndIsActive(Integer clubId, Boolean isActive);
}

