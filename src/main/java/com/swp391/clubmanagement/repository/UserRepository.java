package com.swp391.clubmanagement.repository;

import com.swp391.clubmanagement.entity.Users;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * UserRepository - Spring Data JPA Repository cho entity Users
 * 
 * Interface này kế thừa JpaRepository, cung cấp sẵn các method CRUD cơ bản:
 * - save(), findById(), findAll(), delete(), etc.
 * 
 * Các method được định nghĩa ở đây sử dụng Spring Data JPA Query Method:
 * - Tên method được Spring tự động chuyển thành câu query SQL
 * - Ví dụ: findByEmail() -> SELECT * FROM Users WHERE email = ?
 */
@Repository
public interface UserRepository extends JpaRepository<Users, String> {
    /** Kiểm tra mã sinh viên đã tồn tại chưa: true = đã tồn tại, false = chưa tồn tại */
    boolean existsByStudentCode(String studentCode);
    
    /** Kiểm tra email đã tồn tại chưa: true = đã tồn tại, false = chưa tồn tại */
    boolean existsByEmail(String email);
    
    /** Tìm user theo mã sinh viên: trả về Optional<Users> (có thể rỗng nếu không tìm thấy) */
    Optional<Users> findByStudentCode(String studentCode);
    
    /** Tìm user theo email: trả về Optional<Users> (có thể rỗng nếu không tìm thấy) */
    Optional<Users> findByEmail(String email);
    
    /** Tìm user theo mã xác thực email: dùng để verify email khi user click link trong email */
    Optional<Users> findByVerificationCode(String verificationCode);
    
    /**
     * Lấy danh sách users với eager load registers và các relationship cần thiết
     * 
     * Sử dụng EntityGraph để eager load (load ngay lập tức) các quan hệ:
     * - role: Vai trò của user
     * - registers: Danh sách đơn đăng ký tham gia CLB
     * - registers.membershipPackage: Gói membership của mỗi đơn đăng ký
     * - registers.membershipPackage.club: CLB của mỗi gói membership
     * 
     * Điều này giúp tránh N+1 query problem và populate được clubIds trong UserResponse
     * 
     * @param pageable Phân trang: page number, page size, sort
     * @return Page<Users> chứa danh sách users đã load đầy đủ relationships
     */
    @EntityGraph(attributePaths = {
        "role",
        "registers", 
        "registers.membershipPackage", 
        "registers.membershipPackage.club"
    }, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT DISTINCT u FROM Users u")
    Page<Users> findAllWithRegisters(Pageable pageable);
}
