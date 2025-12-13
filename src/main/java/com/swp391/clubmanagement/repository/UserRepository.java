package com.swp391.clubmanagement.repository;

import com.swp391.clubmanagement.entity.Users;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<Users, String> {
    boolean existsByStudentCode(String studentCode);
    boolean existsByEmail(String email);
    Optional<Users> findByStudentCode(String studentCode);
    Optional<Users> findByEmail(String email);
    Optional<Users> findByVerificationCode(String verificationCode);
    
    /**
     * Lấy danh sách users với eager load registers và các relationship cần thiết để populate clubIds
     * Sử dụng EntityGraph với nested paths để load registers, membershipPackage và club
     */
    @EntityGraph(attributePaths = {
        "registers",
        "registers.membershipPackage", 
        "registers.membershipPackage.club"
    })
    @Query("SELECT DISTINCT u FROM Users u")
    Page<Users> findAllWithRegisters(Pageable pageable);
}
