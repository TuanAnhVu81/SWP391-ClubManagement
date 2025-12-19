package com.swp391.clubmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ClubManagementServiceApplication - Điểm khởi đầu của ứng dụng Spring Boot
 * 
 * Đây là class chính chứa phương thức main() để khởi chạy ứng dụng.
 * Annotation @SpringBootApplication bao gồm:
 * - @Configuration: Đánh dấu class này là nguồn cấu hình bean
 * - @EnableAutoConfiguration: Tự động cấu hình Spring Boot dựa trên dependencies
 * - @ComponentScan: Quét và đăng ký các component trong package này và sub-packages
 */
@SpringBootApplication
public class ClubManagementServiceApplication {

	/**
	 * Phương thức main - Entry point của ứng dụng
	 * Khởi chạy Spring Boot application context và embedded server
	 * 
	 * @param args Các tham số dòng lệnh khi chạy ứng dụng
	 */
	public static void main(String[] args) {
		// Khởi chạy Spring Boot application với class này và các tham số từ command line
		SpringApplication.run(ClubManagementServiceApplication.class, args);
	}

}
