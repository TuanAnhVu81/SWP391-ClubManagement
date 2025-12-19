package com.swp391.clubmanagement.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SwaggerConfig - Cấu hình Swagger/OpenAPI UI cho tài liệu API
 * 
 * Class này cấu hình Swagger UI để tự động tạo tài liệu API tương tác.
 * - Định nghĩa thông tin API (title, version, description)
 * - Cấu hình JWT Bearer Authentication để test các API có yêu cầu xác thực
 * 
 * Sau khi chạy ứng dụng, truy cập: http://localhost:8081/api/swagger-ui.html để xem tài liệu
 */
@Configuration
public class SwaggerConfig {

    /**
     * Bean OpenAPI: Cấu hình thông tin API và phương thức xác thực
     * 
     * @return OpenAPI object chứa cấu hình cho Swagger UI
     */
    @Bean
    public OpenAPI customOpenAPI() {
        // Tên của security scheme (sử dụng trong các endpoint cần xác thực)
        final String securitySchemeName = "bearerAuth";
        
        return new OpenAPI()
                // Thông tin API
                .info(new Info()
                        .title("Club Management API")
                        .version("v1.0")
                        .description("API documentation for Club Management System - FPT University")
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://springdoc.org")))
                // Yêu cầu xác thực mặc định cho tất cả các endpoint (có thể override trong controller)
                .addSecurityItem(new SecurityRequirement()
                        .addList(securitySchemeName))
                // Định nghĩa security scheme: JWT Bearer Token
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")  // Sử dụng Bearer token
                                .bearerFormat("JWT")  // Format là JWT
                                .description("Enter JWT token (without 'Bearer ' prefix)")));
    }
}
