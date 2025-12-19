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

/**
 * RegisterRepository - Spring Data JPA Repository cho entity Registers
 * 
 * Interface này kế thừa JpaRepository, cung cấp sẵn các method CRUD cơ bản.
 * Các method tùy chỉnh dùng để truy vấn đơn đăng ký tham gia CLB với nhiều điều kiện khác nhau.
 */
@Repository
public interface RegisterRepository extends JpaRepository<Registers, Integer> {
    // ============ TÌM KIẾM ĐƠN ĐĂNG KÝ ============
    
    /** Tìm tất cả đơn đăng ký của một user */
    List<Registers> findByUser(Users user);
    
    /** Tìm đơn đăng ký của user với trạng thái cụ thể (Ví dụ: tất cả đơn đang chờ duyệt) */
    List<Registers> findByUserAndStatus(Users user, JoinStatus status);
    
    /** Tìm các đơn đăng ký của user đã tham gia CLB (đã duyệt và đã đóng phí) */
    List<Registers> findByUserAndStatusAndIsPaid(Users user, JoinStatus status, Boolean isPaid);
    
    /** Kiểm tra user đã đăng ký gói membership này chưa (để tránh duplicate registration) */
    boolean existsByUserAndMembershipPackage_PackageId(Users user, Integer packageId);
    
    /** Kiểm tra user đã là thành viên chính thức của CLB này chưa (đã duyệt và đã thanh toán) */
    boolean existsByUserAndMembershipPackage_Club_ClubIdAndStatusAndIsPaid(
            Users user, Integer clubId, JoinStatus status, Boolean isPaid);
    
    /** Tìm đơn đăng ký của user trong một CLB cụ thể (có thể chưa duyệt, đã duyệt...) */
    Optional<Registers> findByUserAndMembershipPackage_Club_ClubId(Users user, Integer clubId);
    
    /** Tìm tất cả đơn đăng ký của một CLB (qua membershipPackage.club.clubId) */
    List<Registers> findByMembershipPackage_Club_ClubId(Integer clubId);
    
    /** Tìm đơn đăng ký của CLB với trạng thái cụ thể (Ví dụ: tất cả đơn đang chờ duyệt của CLB) */
    List<Registers> findByMembershipPackage_Club_ClubIdAndStatus(Integer clubId, JoinStatus status);
    
    // ============ KIỂM TRA VAI TRÒ TRONG CLB ============
    
    /**
     * Kiểm tra user có phải là Leader của CLB không (ChuTich, PhoChuTich, ThuKy)
     * 
     * @param user User cần kiểm tra
     * @param clubId ID của CLB
     * @param roles Danh sách vai trò cần kiểm tra (Ví dụ: [ChuTich, PhoChuTich])
     * @param status Trạng thái đơn (thường là DaDuyet)
     * @param isPaid Đã thanh toán (thường là true)
     * @return true nếu user có một trong các vai trò trong roles và đã duyệt + thanh toán
     */
    boolean existsByUserAndMembershipPackage_Club_ClubIdAndClubRoleInAndStatusAndIsPaid(
            Users user, Integer clubId, List<ClubRoleType> roles, JoinStatus status, Boolean isPaid);
    
    /**
     * Tìm đơn đăng ký của user trong CLB với vai trò cụ thể
     * 
     * @param user User cần tìm
     * @param clubId ID của CLB
     * @param roles Danh sách vai trò (Ví dụ: [ChuTich, PhoChuTich] để tìm leader)
     * @return Optional<Registers> nếu tìm thấy, empty nếu không
     */
    Optional<Registers> findByUserAndMembershipPackage_Club_ClubIdAndClubRoleIn(
            Users user, Integer clubId, List<ClubRoleType> roles);
    
    // ============ TÌM KIẾM THEO THANH TOÁN ============
    
    /** Tìm đơn đăng ký theo PayOS order code (dùng để xử lý webhook thanh toán) */
    Optional<Registers> findByPayosOrderCode(Long orderCode);
    
    // ============ THỐNG KÊ ============
    
    /** Đếm số thành viên chính thức của một CLB (đã duyệt và đã đóng phí) */
    long countByMembershipPackage_Club_ClubIdAndStatusAndIsPaid(Integer clubId, JoinStatus status, Boolean isPaid);
    
    // ============ THỐNG KÊ CHO ADMIN DASHBOARD ============
    
    /** Đếm tổng số thành viên chính thức trong hệ thống (đã duyệt + đã thanh toán) */
    long countByStatusAndIsPaid(JoinStatus status, Boolean isPaid);
    
    /**
     * Đếm số sinh viên duy nhất tham gia CLB (một sinh viên có thể tham gia nhiều CLB nhưng chỉ đếm 1 lần)
     * 
     * @param status Trạng thái (thường là DaDuyet)
     * @param isPaid Đã thanh toán (thường là true)
     * @return Số lượng user duy nhất (sử dụng DISTINCT)
     */
    @Query("SELECT COUNT(DISTINCT r.user) FROM Registers r WHERE r.status = :status AND r.isPaid = :isPaid")
    long countDistinctStudents(@Param("status") JoinStatus status, @Param("isPaid") Boolean isPaid);
    
    /**
     * Đếm số lượng thành viên theo từng vai trò (ClubRoleType)
     * 
     * @param status Trạng thái (thường là DaDuyet)
     * @param isPaid Đã thanh toán (thường là true)
     * @return List<Object[]> với Object[0] = ClubRoleType, Object[1] = Count
     * Ví dụ: [[ThanhVien, 100], [ChuTich, 5], [PhoChuTich, 10]]
     */
    @Query("SELECT r.clubRole, COUNT(r) FROM Registers r WHERE r.status = :status AND r.isPaid = :isPaid GROUP BY r.clubRole")
    List<Object[]> countByClubRole(@Param("status") JoinStatus status, @Param("isPaid") Boolean isPaid);
    
    /**
     * Tìm top CLB có nhiều thành viên nhất (sắp xếp theo số lượng thành viên giảm dần)
     * 
     * @param status Trạng thái (thường là DaDuyet)
     * @param isPaid Đã thanh toán (thường là true)
     * @return List<Object[]> với Object[0] = clubId, Object[1] = clubName, Object[2] = logo,
     *         Object[3] = category, Object[4] = memberCount
     * Sử dụng trong Admin Dashboard để hiển thị top CLB phổ biến nhất
     */
    @Query("SELECT r.membershipPackage.club.clubId, r.membershipPackage.club.clubName, r.membershipPackage.club.logo, " +
           "r.membershipPackage.club.category, COUNT(r) as memberCount " +
           "FROM Registers r WHERE r.status = :status AND r.isPaid = :isPaid " +
           "GROUP BY r.membershipPackage.club.clubId, r.membershipPackage.club.clubName, " +
           "r.membershipPackage.club.logo, r.membershipPackage.club.category " +
           "ORDER BY memberCount DESC")
    List<Object[]> findTopClubsByMemberCount(@Param("status") JoinStatus status, @Param("isPaid") Boolean isPaid);
}
