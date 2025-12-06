package com.swp391.clubmanagement.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import javax.crypto.spec.SecretKeySpec;

/**
 * SecurityConfig: Cấu hình bảo mật chính cho ứng dụng Spring Security.
 * - Định nghĩa các rules phân quyền (Ai được truy cập API nào).
 * - Cấu hình Resource Server để xác thực JWT.
 * - Cấu hình mã hóa mật khẩu.
 * - Cấu hình CORS cho phép Frontend gọi API.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Danh sách các API được phép truy cập công khai (không cần token) - POST
    private final String[] PUBLIC_POST_ENDPOINTS = {
            "/users", "/users/verify", "/users/forgot-password",
            "/auth/login", "/auth/token", "/auth/introspect", 
            "/auth/logout", "/auth/refresh"
    };

    // Danh sách các API được phép truy cập công khai - GET (xác thực email qua link)
    private final String[] PUBLIC_GET_ENDPOINTS = {
            "/users/verify"
    };
    
    // Danh sách các endpoint cho Swagger UI (Tài liệu API)
    private final String[] SWAGGER_ENDPOINTS = {
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    @Value("${jwt.signerKey}")
    private String signerKey;

    /**
     * SecurityFilterChain: Chuỗi bộ lọc bảo mật chặn mọi request HTTP.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(request ->
                request
                        // Cho phép POST đến các public endpoints (đăng ký, login, verify...)
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_ENDPOINTS).permitAll()
                        // Cho phép GET đến link xác thực email
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                        // Cho phép truy cập Swagger UI để xem tài liệu
                        .requestMatchers(SWAGGER_ENDPOINTS).permitAll()
                        // TẤT CẢ các request còn lại BẮT BUỘC phải có token hợp lệ (Authenticated)
                        .anyRequest().authenticated());

        // Cấu hình OAuth2 Resource Server để xử lý JWT Token
        http.oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwtConfigurer ->
                        jwtConfigurer.decoder(jwtDecoder())
                )
        );

        // Disable CSRF (Cross-Site Request Forgery) vì chúng ta dùng stateless REST API (JWT)
        // CSRF thường cần thiết cho các web app dùng Session/Cookie cũ, API hiện đại không cần.
        http.csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }

    /**
     * Bean CorsFilter: Cấu hình CORS để cho phép Frontend gọi API.
     * Cho phép mọi nguồn (Origins), mọi Headers và mọi Methods.
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();

        // Cho phép tất cả các nguồn (origins) truy cập (ví dụ: http://localhost:3000)
        corsConfiguration.addAllowedOrigin("*");
        
        // Cho phép tất cả các method (GET, POST, PUT, DELETE, OPTIONS...)
        corsConfiguration.addAllowedMethod("*");
        
        // Cho phép tất cả các header
        corsConfiguration.addAllowedHeader("*");

        UrlBasedCorsConfigurationSource urlBasedCorsConfigurationSource = new UrlBasedCorsConfigurationSource();
        // Áp dụng cấu hình này cho tất cả các đường dẫn API
        urlBasedCorsConfigurationSource.registerCorsConfiguration("/**", corsConfiguration);

        return new CorsFilter(urlBasedCorsConfigurationSource);
    }

    /**
     * Bean JwtDecoder: Chịu trách nhiệm giải mã và validate JWT Token.
     * Sử dụng NimbusJwtDecoder với thuật toán HS512 và SecretKey đã cấu hình.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec secretKeySpec = new SecretKeySpec(signerKey.getBytes(), "HS512");
        return NimbusJwtDecoder
                .withSecretKey(secretKeySpec)
                .macAlgorithm(MacAlgorithm.HS512)
                .build();
    }

    /**
     * Bean PasswordEncoder: Dùng để mã hóa mật khẩu người dùng.
     * Sử dụng thuật toán BCrypt (mạnh mẽ, an toàn).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
