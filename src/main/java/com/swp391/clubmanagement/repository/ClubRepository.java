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

/**
 * ClubRepository - Spring Data JPA Repository cho entity Clubs
 * 
 * Interface này kế thừa JpaRepository, cung cấp sẵn các method CRUD cơ bản.
 * Các method tùy chỉnh sử dụng Spring Data JPA Query Method hoặc @Query annotation.
 */
@Repository
public interface ClubRepository extends JpaRepository<Clubs, Integer> {
    /** Tìm tất cả CLB đang hoạt động (isActive = true) */
    List<Clubs> findByIsActiveTrue();
    
    /** Tìm CLB theo danh mục và đang hoạt động: Ví dụ tìm tất cả CLB thể thao đang hoạt động */
    List<Clubs> findByCategoryAndIsActiveTrue(ClubCategory category);
    
    /**
     * Tìm kiếm CLB theo tên (tìm kiếm không phân biệt hoa thường, tìm kiếm một phần)
     * 
     * @param name Tên cần tìm kiếm (có thể là một phần của tên)
     * @return Danh sách CLB có tên chứa chuỗi name (case-insensitive)
     */
    @Query("SELECT c FROM Clubs c WHERE c.isActive = true AND LOWER(c.clubName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Clubs> searchByName(@Param("name") String name);
    
    /**
     * Tìm kiếm CLB theo tên và danh mục (cả hai tham số đều có thể null/optional)
     * 
     * @param name Tên cần tìm kiếm (null = bỏ qua điều kiện tên)
     * @param category Danh mục cần tìm (null = bỏ qua điều kiện danh mục)
     * @return Danh sách CLB thỏa mãn các điều kiện
     */
    @Query("SELECT c FROM Clubs c WHERE c.isActive = true " +
           "AND (:name IS NULL OR LOWER(c.clubName) LIKE LOWER(CONCAT('%', :name, '%'))) " +
           "AND (:category IS NULL OR c.category = :category)")
    List<Clubs> searchByNameAndCategory(@Param("name") String name, @Param("category") ClubCategory category);
    
    /**
     * Kiểm tra user có phải là founder (người sáng lập) của club không
     * 
     * @param clubId ID của CLB cần kiểm tra
     * @param founder User cần kiểm tra
     * @return true nếu user là founder của club, false nếu không
     */
    boolean existsByClubIdAndFounder(Integer clubId, Users founder);
    
    /**
     * Tìm tất cả CLB mà user là founder (người sáng lập)
     * 
     * @param founder User cần tìm CLB
     * @return Danh sách CLB mà user là founder
     */
    List<Clubs> findByFounder(Users founder);
    
    /** Đếm tổng số CLB đang hoạt động */
    long countByIsActiveTrue();
    
    /**
     * Đếm số lượng CLB theo từng danh mục (thống kê)
     * 
     * @return List<Object[]> với Object[0] = ClubCategory, Object[1] = Count
     * Ví dụ: [[HocThuat, 5], [TheThao, 3], [NgheThuat, 2]]
     */
    @Query("SELECT c.category, COUNT(c) FROM Clubs c WHERE c.isActive = true GROUP BY c.category")
    List<Object[]> countByCategory();
    
    /**
     * Tìm CLB mới thành lập trong tháng (theo establishedDate)
     * 
     * @param startOfMonth Ngày bắt đầu của tháng (để so sánh với establishedDate)
     * @return Danh sách CLB được thành lập từ startOfMonth đến nay, sắp xếp theo ngày thành lập (mới nhất trước)
     */
    @Query("SELECT c FROM Clubs c WHERE c.isActive = true AND c.establishedDate >= :startOfMonth ORDER BY c.establishedDate DESC")
    List<Clubs> findNewClubsThisMonth(@Param("startOfMonth") LocalDate startOfMonth);
}
