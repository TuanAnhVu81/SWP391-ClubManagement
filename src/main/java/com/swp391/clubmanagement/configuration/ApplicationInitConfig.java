package com.swp391.clubmanagement.configuration;

import com.swp391.clubmanagement.entity.Roles;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.enums.RoleType;
import com.swp391.clubmanagement.repository.RoleRepository;
import com.swp391.clubmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ApplicationInitConfig {

    private final PasswordEncoder passwordEncoder;

    @Bean
    ApplicationRunner applicationRunner(UserRepository userRepository, RoleRepository roleRepository) {
        return args -> {
            log.info("Initializing application...");

            // 1. Init Roles if not exist
            if (roleRepository.count() == 0) {
                roleRepository.save(Roles.builder().roleName(RoleType.QuanTriVien).build());
                roleRepository.save(Roles.builder().roleName(RoleType.SinhVien).build());
                log.info("Default roles initialized.");
            }

            // 2. Init Admin User if not exist
            if (!userRepository.existsByStudentCode("ADMIN001")) {
                Roles adminRole = roleRepository.findByRoleName(RoleType.QuanTriVien)
                        .orElseThrow(() -> new RuntimeException("Admin role not found"));

                Users adminUser = Users.builder()
                        .studentCode("ADMIN001")
                        .fullName("System Admin")
                        .email("admin@gmail.com")
                        .password(passwordEncoder.encode("admin123"))
                        .role(adminRole)
                        .build();

                userRepository.save(adminUser);
                log.info("Admin user initialized: ADMIN001 / admin123");
            } else {
                log.info("Admin user already exists.");
            }
            
            log.info("Application initialization completed.");
        };
    }
}

