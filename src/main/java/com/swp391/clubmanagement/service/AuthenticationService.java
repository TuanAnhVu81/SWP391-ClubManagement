// Package định nghĩa service layer - xử lý authentication và JWT token
package com.swp391.clubmanagement.service;

// ========== JWT Library (Nimbus JOSE + JWT) ==========
import com.nimbusds.jose.*; // JOSE (JSON Object Signing and Encryption) - xử lý JWT
import com.nimbusds.jose.crypto.MACSigner; // Ký JWT token bằng MAC (Message Authentication Code)
import com.nimbusds.jose.crypto.MACVerifier; // Xác minh chữ ký JWT token
import com.nimbusds.jwt.JWTClaimsSet; // Payload của JWT (claims)
import com.nimbusds.jwt.SignedJWT; // JWT đã được ký

// ========== DTO ==========
import com.swp391.clubmanagement.dto.request.AuthenticationRequest; // Request đăng nhập
import com.swp391.clubmanagement.dto.request.IntrospectRequest; // Request kiểm tra token
import com.swp391.clubmanagement.dto.request.LogoutRequest; // Request đăng xuất
import com.swp391.clubmanagement.dto.response.AuthenticationResponse; // Response đăng nhập (chứa token)
import com.swp391.clubmanagement.dto.response.IntrospectResponse; // Response kiểm tra token (valid/invalid)

// ========== Entity ==========
import com.swp391.clubmanagement.entity.Users; // Entity người dùng

// ========== Enum ==========
import com.swp391.clubmanagement.enums.JoinStatus; // Trạng thái tham gia CLB

// ========== Exception ==========
import com.swp391.clubmanagement.exception.AppException; // Custom exception
import com.swp391.clubmanagement.exception.ErrorCode; // Mã lỗi hệ thống

// ========== Repository ==========
import com.swp391.clubmanagement.repository.ClubRepository; // Repository cho bảng Clubs
import com.swp391.clubmanagement.repository.RegisterRepository; // Repository cho bảng Registers
import com.swp391.clubmanagement.repository.UserRepository; // Repository cho bảng Users

// ========== Lombok ==========
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor; // Tự động tạo constructor inject dependencies
import lombok.experimental.FieldDefaults; // Tự động thêm private final cho fields
import lombok.experimental.NonFinal; // Cho phép field không final (dùng với @Value)
import lombok.extern.slf4j.Slf4j; // Tự động tạo logger

// ========== Spring Framework ==========
import org.springframework.beans.factory.annotation.Value; // Inject giá trị từ application.properties
import org.springframework.security.crypto.password.PasswordEncoder; // Mã hóa/so sánh mật khẩu (BCrypt)
import org.springframework.stereotype.Service; // Đánh dấu class là Spring Service Bean

// ========== Java Standard Library ==========
import java.text.ParseException; // Exception khi parse JWT token
import java.time.Instant; // Thời điểm cụ thể
import java.time.temporal.ChronoUnit; // Đơn vị thời gian (HOURS, DAYS, etc.)
import java.util.Date; // Ngày giờ
import java.util.List; // Danh sách
import java.util.stream.Collectors; // Collect stream thành collection

/**
 * AuthenticationService - Service xử lý logic xác thực và quản lý JWT token
 * 
 * Service này chịu trách nhiệm:
 * 1. Xác thực người dùng (đăng nhập): Kiểm tra email, mật khẩu, trạng thái tài khoản
 * 2. Tạo JWT token: Sinh token chứa thông tin user và quyền truy cập
 * 3. Xác minh token: Kiểm tra tính hợp lệ của token (chữ ký, thời hạn)
 * 4. Đăng xuất: Xử lý logout (có thể mở rộng thêm blacklist token)
 * 
 * Sử dụng thuật toán HS512 để ký và xác minh JWT token.
 */
/**
 * Service xử lý logic xác thực và quản lý JWT token
 * 
 * Chức năng chính:
 * 1. Xác thực người dùng (đăng nhập): Kiểm tra email, mật khẩu, trạng thái tài khoản
 * 2. Tạo JWT token: Sinh token chứa thông tin user và quyền truy cập
 * 3. Xác minh token: Kiểm tra tính hợp lệ của token (chữ ký, thời hạn)
 * 4. Đăng xuất: Xử lý logout (có thể mở rộng thêm blacklist token)
 * 
 * Sử dụng thuật toán HS512 (HMAC-SHA512) để ký và xác minh JWT token
 * 
 * @Service: Spring Service Bean, được quản lý bởi IoC Container
 * @RequiredArgsConstructor: Lombok tự động tạo constructor inject dependencies
 * @FieldDefaults: Tự động thêm private final cho các field
 * @Slf4j: Tự động tạo logger với tên "log"
 */
@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationService {
    /** Repository thao tác với bảng users - tìm user theo email */
    UserRepository userRepository;
    
    /** PasswordEncoder để mã hóa và so sánh mật khẩu (BCrypt) */
    PasswordEncoder passwordEncoder;
    
    /** Repository thao tác với bảng clubs - lấy CLB mà user là founder */
    ClubRepository clubRepository;
    
    /** Repository thao tác với bảng registers - lấy CLB mà user là member */
    RegisterRepository registerRepository;

    /** Secret key để ký và xác minh JWT token (đọc từ application.properties) */
    @NonFinal
    @Value("${jwt.signerKey}")
    protected String SIGNER_KEY;

    /**
     * introspect: Kiểm tra tính hợp lệ của token (dùng cho Resource Server hoặc Gateway).
     * @param request chứa token cần kiểm tra
     * @return kết quả valid: true/false
     */
    public IntrospectResponse introspect(IntrospectRequest request) throws JOSEException, ParseException {
        var token = request.getToken();
        boolean isValid = true;

        try {
            // Verify signature và expiration của token
            verifyToken(token);
        } catch (AppException e) {
            isValid = false;
        }

        return IntrospectResponse.builder()
                .valid(isValid)
                .build();
    }

    /**
     * authenticate: Xử lý đăng nhập.
     * 1. Kiểm tra user tồn tại (theo email).
     * 2. Kiểm tra tài khoản có bị vô hiệu hóa không (isActive).
     * 3. Kiểm tra tài khoản đã xác thực email chưa (enabled).
     * 4. Khớp mật khẩu (dùng BCrypt).
     * 5. Nếu đúng -> Tạo token.
     */
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        // 1. Kiểm tra Email có tồn tại không
        var user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.EMAIL_NOT_EXIST));

        // 2. Kiểm tra tài khoản có bị vô hiệu hóa bởi Admin không (isActive = false)
        if (user.getIsActive() == null || !user.getIsActive()) {
            throw new AppException(ErrorCode.USER_DEACTIVATED);
        }

        // 3. Kiểm tra tài khoản đã xác thực (enable) chưa
        // Lưu ý: Trường 'enabled' trong Entity Users mặc định là false khi mới tạo
        if (!user.isEnabled()) {
            throw new AppException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        // 4. Kiểm tra mật khẩu
        boolean authenticated = passwordEncoder.matches(request.getPassword(), user.getPassword());
        if (!authenticated) {
            throw new AppException(ErrorCode.WRONG_PASSWORD);
        }

        // 5. Nếu tất cả OK -> Tạo Token JWT
        var token = generateToken(user);

        return AuthenticationResponse.builder()
                .token(token)
                .authenticated(true)
                .build();
    }

    /**
     * logout: Xử lý đăng xuất.
     * Hiện tại chỉ verify token, sau này sẽ thêm logic Blacklist token vào Redis/DB
     * để chặn các token còn hạn nhưng user đã logout.
     */
    public void logout(LogoutRequest request) throws ParseException, JOSEException {
        try {
            verifyToken(request.getToken());
            // TODO: Logic blacklist token sẽ được implement tại đây (lưu jti + expiryTime)
        } catch (AppException e) {
            log.info("Token already invalid");
        }
    }

    /**
     * verifyToken: Xác thực token.
     * - Parse token string thành SignedJWT.
     * - Verify chữ ký bằng SIGNER_KEY.
     * - Kiểm tra thời gian hết hạn (Expiration Time).
     */
    private SignedJWT verifyToken(String token) throws JOSEException, ParseException {
        JWSVerifier verifier = new MACVerifier(SIGNER_KEY.getBytes());

        SignedJWT signedJWT = SignedJWT.parse(token);

        Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();

        var verified = signedJWT.verify(verifier);

        // Token hợp lệ khi: Chữ ký đúng VÀ chưa hết hạn
        if (!(verified && expiryTime.after(new Date())))
            throw new AppException(ErrorCode.UNAUTHENTICATED);

        return signedJWT;
    }

    /**
     * generateToken: Tạo JWT Token mới.
     * - Header: Thuật toán HS512.
     * - Payload: sub (email), iss (issuer), iat (issued at), exp (expiration), scope (ROLE_...).
     * - Signature: Ký bằng SIGNER_KEY.
     */
    private String generateToken(Users user) {
        // 1. Tạo Header
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        // 2. Tạo Payload (Claims)
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(user.getEmail()) // Subject thường là định danh người dùng (email/username)
                .issuer("swp391.com") // Người phát hành token
                .issueTime(new Date()) // Thời điểm phát hành
                .expirationTime(new Date(
                        Instant.now().plus(2, ChronoUnit.HOURS).toEpochMilli() // Hết hạn sau 2 tiếng
                ))
                .claim("userId", user.getUserId()) // Thêm custom claim userId
                .claim("scope", buildScope(user)) // Thêm claim scope (Role)
                .claim("clubIds", getClubIds(user)) // Thêm danh sách clubId mà user là leader hoặc member
                .build();

        Payload payload = new Payload(jwtClaimsSet.toJSONObject());

        JWSObject jwsObject = new JWSObject(header, payload);

        // 3. Ký Token
        try {
            jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes()));
            return jwsObject.serialize(); // Trả về chuỗi JWT hoàn chỉnh
        } catch (JOSEException e) {
            log.error("Cannot create token", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * buildScope: Tạo chuỗi scope từ Role của user.
     * Không thêm prefix "ROLE_" vì Spring Security sẽ tự động thêm "SCOPE_"
     * Ví dụ: "SinhVien" -> JWT claim "scope": "SinhVien" -> Authority: "SCOPE_SinhVien"
     */
    private String buildScope(Users user) {
        if (user.getRole() != null) {
            return user.getRole().getRoleName().name();
        }
        return "";
    }
    
    /**
     * getClubIds: Lấy danh sách clubId mà user là leader (ChuTich) hoặc member.
     * - Nếu user là ChuTich (founder): Lấy CLB mà họ là founder
     * - Nếu user có ClubRole = ChuTich, PhoChuTich, ThuKy: Lấy CLB đó
     * - Nếu user là member thường: Lấy tất cả CLB mà họ đã tham gia (đã duyệt và đã đóng phí)
     */
    private List<Integer> getClubIds(Users user) {
        // 1. Lấy CLB mà user là founder
        List<Integer> foundedClubIds = clubRepository.findByFounder(user).stream()
                .map(club -> club.getClubId())
                .collect(Collectors.toList());
        
        // 2. Lấy CLB mà user là leader (ChuTich, PhoChuTich, ThuKy) hoặc member
        List<Integer> memberClubIds = registerRepository.findByUser(user).stream()
                .filter(reg -> reg.getStatus() == JoinStatus.DaDuyet && reg.getIsPaid())
                .filter(reg -> reg.getMembershipPackage() != null && reg.getMembershipPackage().getClub() != null)
                .map(reg -> reg.getMembershipPackage().getClub().getClubId())
                .distinct()
                .collect(Collectors.toList());
        
        // 3. Gộp và loại bỏ trùng lặp
        foundedClubIds.addAll(memberClubIds);
        return foundedClubIds.stream()
                .distinct()
                .collect(Collectors.toList());
    }
}

