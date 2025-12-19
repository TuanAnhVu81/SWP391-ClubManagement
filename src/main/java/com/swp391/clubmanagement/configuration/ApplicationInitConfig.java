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

/**
 * ApplicationInitConfig - Cấu hình khởi tạo dữ liệu ban đầu khi ứng dụng khởi động
 * 
 * Class này chạy khi ứng dụng Spring Boot khởi động, thực hiện:
 * 1. Tạo các Role mặc định (QuanTriVien, SinhVien, ChuTich) nếu chưa có
 * 2. Tạo tài khoản Admin mặc định để quản lý hệ thống
 * 
 * Chỉ tạo nếu chưa tồn tại, tránh duplicate khi restart ứng dụng nhiều lần.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ApplicationInitConfig {

    // PasswordEncoder để mã hóa mật khẩu Admin
    private final PasswordEncoder passwordEncoder;

    /**
     * Bean ApplicationRunner: Chạy sau khi Spring Boot context đã sẵn sàng
     * 
     * Thực hiện khởi tạo dữ liệu ban đầu:
     * - Tạo các Role mặc định nếu chưa có
     * - Tạo tài khoản Admin mặc định nếu chưa có
     * 
     * @param userRepository Repository để thao tác với bảng Users
     * @param roleRepository Repository để thao tác với bảng Roles
     * @return ApplicationRunner sẽ được Spring Boot tự động chạy khi khởi động
     */
    @Bean
    ApplicationRunner applicationRunner(UserRepository userRepository, RoleRepository roleRepository) {
        return args -> {
            log.info("Initializing application...");

            // ========== 1. KHỞI TẠO CÁC ROLE MẶC ĐỊNH ==========
            // Kiểm tra nếu chưa có role nào trong database
            if (roleRepository.count() == 0) {
                // Tạo 3 role mặc định: Quản trị viên, Sinh viên, Chủ tịch CLB
                roleRepository.save(Roles.builder().roleName(RoleType.QuanTriVien).build());
                roleRepository.save(Roles.builder().roleName(RoleType.SinhVien).build());
                roleRepository.save(Roles.builder().roleName(RoleType.ChuTich).build());
                log.info("Default roles initialized.");
            } else {
                // Nếu đã có role nhưng thiếu role ChuTich (migration cho database đã có dữ liệu cũ)
                if (!roleRepository.findByRoleName(RoleType.ChuTich).isPresent()) {
                    roleRepository.save(Roles.builder().roleName(RoleType.ChuTich).build());
                    log.info("ChuTich role added to existing roles.");
                }
            }

            // ========== 2. KHỞI TẠO TÀI KHOẢN ADMIN MẶC ĐỊNH ==========
            // Kiểm tra nếu chưa có user với mã sinh viên "ADMIN001"
            if (!userRepository.existsByStudentCode("ADMIN001")) {
                // Lấy role QuanTriVien (phải tồn tại sau bước 1)
                Roles adminRole = roleRepository.findByRoleName(RoleType.QuanTriVien)
                        .orElseThrow(() -> new RuntimeException("Admin role not found"));

                // Tạo user Admin với thông tin mặc định
                Users adminUser = Users.builder()
                        .studentCode("ADMIN001")
                        .fullName("System Admin")
                        .email("admin@gmail.com")
                        .enabled(true)  // Đã kích hoạt, không cần verify email
                        .password(passwordEncoder.encode("admin123"))  // Mã hóa mật khẩu
                        .role(adminRole)  // Gán role QuanTriVien
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

