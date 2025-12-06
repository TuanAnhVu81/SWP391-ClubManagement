package com.swp391.clubmanagement.repository;

import com.swp391.clubmanagement.entity.Registers;
import com.swp391.clubmanagement.enums.JoinStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegisterRepository extends JpaRepository<Registers, Integer> {
    
    // Lấy danh sách thành viên đã được chấp thuận trong CLB
    @Query("SELECT r FROM Registers r WHERE r.membershipPackage.club.clubId = :clubId AND r.status = :status")
    List<Registers> findByClubIdAndStatus(@Param("clubId") Integer clubId, @Param("status") JoinStatus status);
}
