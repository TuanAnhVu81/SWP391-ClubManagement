package com.swp391.clubmanagement.repository;

import com.swp391.clubmanagement.entity.Clubs;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.enums.ClubCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ClubRepository extends JpaRepository<Clubs, Integer> {
    
    // Tìm tất cả CLB đang hoạt động
    List<Clubs> findByIsActiveTrue();
    
    // Tìm CLB theo category và đang hoạt động
    List<Clubs> findByCategoryAndIsActiveTrue(ClubCategory category);
    
    // Tìm CLB theo tên (search)
    @Query("SELECT c FROM Clubs c WHERE c.isActive = true AND LOWER(c.clubName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Clubs> searchByName(@Param("name") String name);
    
    // Tìm CLB theo tên và category
    @Query("SELECT c FROM Clubs c WHERE c.isActive = true " +
           "AND (:name IS NULL OR LOWER(c.clubName) LIKE LOWER(CONCAT('%', :name, '%'))) " +
           "AND (:category IS NULL OR c.category = :category)")
    List<Clubs> searchByNameAndCategory(@Param("name") String name, @Param("category") ClubCategory category);
    
    // Kiểm tra user có phải là founder của club không
    boolean existsByClubIdAndFounder(Integer clubId, Users founder);
    
    // Đếm tổng số CLB active
    long countByIsActiveTrue();
    
    // Đếm CLB theo category
    @Query("SELECT c.category, COUNT(c) FROM Clubs c WHERE c.isActive = true GROUP BY c.category")
    List<Object[]> countByCategory();
    
    // Tìm CLB mới trong tháng (theo establishedDate)
    @Query("SELECT c FROM Clubs c WHERE c.isActive = true AND c.establishedDate >= :startOfMonth ORDER BY c.establishedDate DESC")
    List<Clubs> findNewClubsThisMonth(@Param("startOfMonth") LocalDate startOfMonth);
}
