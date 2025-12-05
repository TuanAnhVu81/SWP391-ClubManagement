package com.swp391.clubmanagement.configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.context.annotation.Configuration;

//@Profile("dev")
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "ClubManagement API", version = "v1"),
        security = @SecurityRequirement(name = "bearerAuth") // Mặc định áp dụng bearerAuth cho toàn bộ API
)

public class SwaggerConfig {
}
