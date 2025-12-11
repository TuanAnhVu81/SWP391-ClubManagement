package com.swp391.clubmanagement.repository;

import com.swp391.clubmanagement.entity.Registers;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.enums.ClubRoleType;
import com.swp391.clubmanagement.enums.JoinStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegisterRepository extends JpaRepository<Registers, Integer> {
    
    // Tìm đăng ký theo user
    List<Registers> findByUser(Users user);
    
    // Tìm đăng ký theo user và trạng thái
    List<Registers> findByUserAndStatus(Users user, JoinStatus status);
    
    // Kiểm tra user đã đăng ký gói này chưa
    boolean existsByUserAndMembershipPackage_PackageId(Users user, Integer packageId);
    
    // Kiểm tra user đã là thành viên CLB này chưa (đã duyệt và đã thanh toán)
    boolean existsByUserAndMembershipPackage_Club_ClubIdAndStatusAndIsPaid(
            Users user, Integer clubId, JoinStatus status, Boolean isPaid);
    
    // Tìm đăng ký theo user và club
    Optional<Registers> findByUserAndMembershipPackage_Club_ClubId(Users user, Integer clubId);
    
    // Tìm tất cả đăng ký của 1 CLB (theo club_id qua membershipPackage)
    List<Registers> findByMembershipPackage_Club_ClubId(Integer clubId);
    
    // Tìm đăng ký theo club và trạng thái
    List<Registers> findByMembershipPackage_Club_ClubIdAndStatus(Integer clubId, JoinStatus status);
    
    // Kiểm tra user có phải là Leader của CLB không (ChuTich hoặc PhoChuTich, đã duyệt, đã đóng phí)
    boolean existsByUserAndMembershipPackage_Club_ClubIdAndClubRoleInAndStatusAndIsPaid(
            Users user, Integer clubId, List<ClubRoleType> roles, JoinStatus status, Boolean isPaid);
    
    // Tìm đăng ký của user trong CLB với role cụ thể
    Optional<Registers> findByUserAndMembershipPackage_Club_ClubIdAndClubRoleIn(
            Users user, Integer clubId, List<ClubRoleType> roles);
    
    // Tìm đăng ký theo PayOS order code
    Optional<Registers> findByPayosOrderCode(Long orderCode);
    
    // ============ THỐNG KÊ CHO ADMIN DASHBOARD ============
    
    // Đếm tổng số thành viên chính thức (đã duyệt + đã thanh toán)
    long countByStatusAndIsPaid(JoinStatus status, Boolean isPaid);
    
    // Đếm số sinh viên duy nhất tham gia CLB
    @Query("SELECT COUNT(DISTINCT r.user) FROM Registers r WHERE r.status = :status AND r.isPaid = :isPaid")
    long countDistinctStudents(@Param("status") JoinStatus status, @Param("isPaid") Boolean isPaid);
    
    // Đếm thành viên theo vai trò (ClubRoleType)
    @Query("SELECT r.clubRole, COUNT(r) FROM Registers r WHERE r.status = :status AND r.isPaid = :isPaid GROUP BY r.clubRole")
    List<Object[]> countByClubRole(@Param("status") JoinStatus status, @Param("isPaid") Boolean isPaid);
    
    // Top N CLB có nhiều thành viên nhất
    @Query("SELECT r.membershipPackage.club.clubId, r.membershipPackage.club.clubName, r.membershipPackage.club.logo, " +
           "r.membershipPackage.club.category, COUNT(r) as memberCount " +
           "FROM Registers r WHERE r.status = :status AND r.isPaid = :isPaid " +
           "GROUP BY r.membershipPackage.club.clubId, r.membershipPackage.club.clubName, " +
           "r.membershipPackage.club.logo, r.membershipPackage.club.category " +
           "ORDER BY memberCount DESC")
    List<Object[]> findTopClubsByMemberCount(@Param("status") JoinStatus status, @Param("isPaid") Boolean isPaid);
}
