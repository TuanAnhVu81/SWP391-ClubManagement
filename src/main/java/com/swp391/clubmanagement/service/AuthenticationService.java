package com.swp391.clubmanagement.service;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.swp391.clubmanagement.dto.request.AuthenticationRequest;
import com.swp391.clubmanagement.dto.request.IntrospectRequest;
import com.swp391.clubmanagement.dto.request.LogoutRequest;
import com.swp391.clubmanagement.dto.response.AuthenticationResponse;
import com.swp391.clubmanagement.dto.response.IntrospectResponse;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.enums.ClubRoleType;
import com.swp391.clubmanagement.enums.JoinStatus;
import com.swp391.clubmanagement.enums.RoleType;
import com.swp391.clubmanagement.exception.AppException;
import com.swp391.clubmanagement.exception.ErrorCode;
import com.swp391.clubmanagement.repository.ClubRepository;
import com.swp391.clubmanagement.repository.RegisterRepository;
import com.swp391.clubmanagement.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AuthenticationService: Service xử lý logic đăng nhập, đăng xuất và quản lý token.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationService {
    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    ClubRepository clubRepository;
    RegisterRepository registerRepository;

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
     * 2. Khớp mật khẩu (dùng BCrypt).
     * 3. Nếu đúng -> Tạo token.
     */
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        // 1. Kiểm tra Email có tồn tại không
        var user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.EMAIL_NOT_EXIST));

        // 2. Kiểm tra tài khoản đã xác thực (enable) chưa
        // Lưu ý: Trường 'enabled' trong Entity Users mặc định là false khi mới tạo
        if (!user.isEnabled()) {
            throw new AppException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        // 3. Kiểm tra mật khẩu
        boolean authenticated = passwordEncoder.matches(request.getPassword(), user.getPassword());
        if (!authenticated) {
            throw new AppException(ErrorCode.WRONG_PASSWORD);
        }

        // // 4. Nếu tất cả OK -> Tạo Token JWT
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
            var signToken = verifyToken(request.getToken());
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

