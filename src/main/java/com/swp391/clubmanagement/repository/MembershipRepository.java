package com.swp391.clubmanagement.repository;

import com.swp391.clubmanagement.entity.Memberships;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MembershipRepository extends JpaRepository<Memberships, Integer> {
    
    // Tìm các gói thành viên của 1 CLB
    List<Memberships> findByClub_ClubId(Integer clubId);
    
    // Tìm các gói đang active của 1 CLB
    List<Memberships> findByClub_ClubIdAndIsActive(Integer clubId, Boolean isActive);
}

