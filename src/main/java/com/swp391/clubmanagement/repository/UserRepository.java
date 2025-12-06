package com.swp391.clubmanagement.repository;

import com.swp391.clubmanagement.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<Users, String> {
    boolean existsByStudentCode(String studentCode);
    boolean existsByEmail(String email);
    Optional<Users> findByStudentCode(String studentCode);
    Optional<Users> findByEmail(String email);
    Optional<Users> findByVerificationCode(String verificationCode);
}
