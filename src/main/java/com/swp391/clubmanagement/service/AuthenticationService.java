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
import com.swp391.clubmanagement.enums.JoinStatus;
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
 * AuthenticationService - Service xử lý logic xác thực và quản lý JWT token
 * 
 * Service này là trung tâm của hệ thống authentication, chịu trách nhiệm:
 * 1. Xác thực người dùng (đăng nhập): Kiểm tra email, mật khẩu, trạng thái tài khoản
 * 2. Tạo JWT token: Sinh token chứa thông tin user, role, và danh sách clubIds
 * 3. Xác minh token: Kiểm tra tính hợp lệ của token (chữ ký, thời hạn) - dùng cho Resource Server
 * 4. Đăng xuất: Xử lý logout (hiện tại chỉ verify token, có thể mở rộng thêm blacklist token)
 * 
 * JWT Token Structure:
 * - Algorithm: HS512 (HMAC with SHA-512)
 * - Claims: sub (email), iss (issuer), iat (issued at), exp (expiration - 2 giờ), 
 *           userId, scope (role), clubIds (danh sách CLB mà user là leader/member)
 * - Signature: Ký bằng SIGNER_KEY (secret key từ config)
 * 
 * Security Flow:
 * 1. User login → authenticate() → verify credentials → generateToken()
 * 2. Client gửi token trong header: "Authorization: Bearer {token}"
 * 3. Spring Security filter (JwtDecoder) verify token → extract claims
 * 4. SecurityContext được populate với authentication information
 * 
 * @Service - Đánh dấu đây là một Spring Service, được quản lý bởi Spring Container
 * @RequiredArgsConstructor - Lombok tự động tạo constructor với các field final để dependency injection
 * @FieldDefaults - Lombok: tất cả field là PRIVATE và FINAL (immutable dependencies)
 * @Slf4j - Lombok: tự động tạo logger với tên "log" để ghi log
 */
@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationService {
    /**
     * Repository để truy vấn và thao tác với bảng Users trong database
     * Dùng để tìm user khi đăng nhập và lấy thông tin user khi tạo token
     */
    UserRepository userRepository;
    
    /**
     * PasswordEncoder để so sánh mật khẩu khi đăng nhập
     * Sử dụng BCrypt algorithm để hash và verify password
     */
    PasswordEncoder passwordEncoder;
    
    /**
     * Repository để truy vấn và thao tác với bảng Clubs trong database
     * Dùng để lấy danh sách CLB mà user là founder (khi tạo token)
     */
    ClubRepository clubRepository;
    
    /**
     * Repository để truy vấn và thao tác với bảng Registers trong database
     * Dùng để lấy danh sách CLB mà user là member/leader (khi tạo token)
     */
    RegisterRepository registerRepository;

    /**
     * Secret key để ký và xác minh JWT token
     * Được inject từ application.yaml qua @Value annotation
     * Phải giữ bí mật, không được commit vào git (nên dùng application-secret.yaml)
     * 
     * @NonFinal - Field này không phải final vì cần có thể inject giá trị từ config
     */
    @NonFinal
    @Value("${jwt.signerKey}")
    protected String SIGNER_KEY;

    /**
     * Kiểm tra tính hợp lệ của JWT token (Introspection endpoint)
     * 
     * Phương thức này được dùng để verify token từ bên ngoài (ví dụ: Resource Server, API Gateway).
     * Đây là endpoint chuẩn OAuth2 để kiểm tra token có còn hợp lệ không.
     * 
     * Use cases:
     * - API Gateway muốn verify token trước khi forward request
     * - Resource Server muốn validate token độc lập
     * - Client muốn check token có còn hạn không trước khi gọi API
     * 
     * @param request - DTO chứa token cần kiểm tra
     * @return IntrospectResponse - Chứa field "valid" (true/false) cho biết token có hợp lệ không
     * @throws JOSEException - Lỗi liên quan đến JWT/JOSE (ví dụ: parse error)
     * @throws ParseException - Lỗi parse token string
     * 
     * Lưu ý: Method này không throw exception nếu token không hợp lệ,
     *        mà trả về valid=false trong response (theo chuẩn OAuth2)
     */
    public IntrospectResponse introspect(IntrospectRequest request) throws JOSEException, ParseException {
        // Lấy token từ request
        var token = request.getToken();
        boolean isValid = true;

        try {
            // Gọi verifyToken() để kiểm tra chữ ký và thời hạn
            // Nếu token hợp lệ, verifyToken() sẽ return SignedJWT
            // Nếu token không hợp lệ, verifyToken() sẽ throw AppException
            verifyToken(token);
        } catch (AppException e) {
            // Token không hợp lệ (chữ ký sai, đã hết hạn, format sai...)
            // Set valid = false thay vì throw exception
            isValid = false;
        }

        // Trả về response với kết quả valid
        return IntrospectResponse.builder()
                .valid(isValid)
                .build();
    }

    /**
     * Xử lý đăng nhập và tạo JWT token
     * 
     * Phương thức này là entry point cho quá trình đăng nhập của user.
     * Thực hiện xác thực đầy đủ các bước bảo mật trước khi cấp token.
     * 
     * Quy trình xác thực (theo thứ tự):
     * 1. Kiểm tra email có tồn tại trong database không
     * 2. Kiểm tra tài khoản có bị vô hiệu hóa bởi Admin không (isActive = false)
     * 3. Kiểm tra tài khoản đã xác thực email chưa (enabled = true)
     * 4. Kiểm tra mật khẩu có khớp không (dùng BCrypt matching)
     * 5. Nếu tất cả đều OK → Tạo JWT token và trả về
     * 
     * @param request - DTO chứa email và password từ client
     * @return AuthenticationResponse - Chứa JWT token và flag authenticated=true
     * @throws AppException với ErrorCode.EMAIL_NOT_EXIST nếu email không tồn tại
     * @throws AppException với ErrorCode.USER_DEACTIVATED nếu tài khoản bị vô hiệu hóa
     * @throws AppException với ErrorCode.EMAIL_NOT_VERIFIED nếu chưa verify email
     * @throws AppException với ErrorCode.WRONG_PASSWORD nếu mật khẩu sai
     * 
     * Lưu ý bảo mật:
     * - Không tiết lộ thông tin chi tiết về lỗi (ví dụ: không nói "email không tồn tại" vs "mật khẩu sai")
     *   để tránh user enumeration attack (nhưng trong thực tế, nhiều hệ thống vẫn làm vậy)
     * - Mật khẩu được so sánh bằng BCrypt (one-way hash, an toàn)
     * - Token có thời hạn 2 giờ (có thể config)
     */
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        // Bước 1: Kiểm tra Email có tồn tại trong database không
        // Nếu không tìm thấy, throw exception với error code phù hợp
        var user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.EMAIL_NOT_EXIST));

        // Bước 2: Kiểm tra tài khoản có bị vô hiệu hóa bởi Admin không
        // Admin có thể deactivate user (soft delete), user đó không thể đăng nhập
        // isActive = false nghĩa là tài khoản đã bị vô hiệu hóa
        if (user.getIsActive() == null || !user.getIsActive()) {
            throw new AppException(ErrorCode.USER_DEACTIVATED);
        }

        // Bước 3: Kiểm tra tài khoản đã xác thực email chưa
        // enabled = false nghĩa là user chưa click link xác thực trong email
        // Chỉ cho phép đăng nhập sau khi đã verify email (enabled = true)
        // Lưu ý: Trường 'enabled' trong Entity Users mặc định là false khi mới tạo account
        if (!user.isEnabled()) {
            throw new AppException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        // Bước 4: Kiểm tra mật khẩu có khớp không
        // passwordEncoder.matches() sẽ hash password từ request và so sánh với hash trong database
        // BCrypt matching là secure comparison, không thể reverse để lấy password gốc
        boolean authenticated = passwordEncoder.matches(request.getPassword(), user.getPassword());
        if (!authenticated) {
            throw new AppException(ErrorCode.WRONG_PASSWORD);
        }

        // Bước 5: Tất cả các bước xác thực đều OK → Tạo JWT token
        // Token sẽ chứa thông tin user, role, và danh sách clubIds
        var token = generateToken(user);

        // Trả về response với token và flag authenticated
        return AuthenticationResponse.builder()
                .token(token)              // JWT token để client dùng cho các request sau
                .authenticated(true)       // Flag xác nhận đăng nhập thành công
                .build();
    }

    /**
     * Xử lý đăng xuất (Logout)
     * 
     * Phương thức này xử lý quá trình đăng xuất của user.
     * Hiện tại chỉ verify token để đảm bảo token hợp lệ trước khi logout.
     * 
     * TODO - Cần cải thiện trong tương lai:
     * - Thêm logic blacklist token vào Redis hoặc database
     * - Lưu JTI (JWT ID) và expiry time của token vào blacklist
     * - Khi verify token, check xem token có trong blacklist không
     * - Điều này sẽ chặn các token còn hạn nhưng user đã logout
     * 
     * Lưu ý: Với JWT stateless, không có cách nào "vô hiệu hóa" token ngay lập tức
     *        Token vẫn còn hợp lệ cho đến khi hết hạn (exp). Blacklist là giải pháp tốt nhất.
     * 
     * @param request - DTO chứa token cần logout
     * @throws ParseException - Lỗi parse token string
     * @throws JOSEException - Lỗi liên quan đến JWT/JOSE
     * 
     * Hiện tại: Nếu token đã không hợp lệ (hết hạn, chữ ký sai...), chỉ log info và return
     *           Không throw exception vì user có thể đã logout rồi hoặc token đã hết hạn
     */
    public void logout(LogoutRequest request) throws ParseException, JOSEException {
        try {
            // Verify token để đảm bảo format đúng
            // Nếu token hợp lệ, có thể thêm vào blacklist
            verifyToken(request.getToken());
            
            // TODO: Logic blacklist token sẽ được implement tại đây
            // Ví dụ:
            // 1. Extract JTI từ token (nếu có)
            // 2. Lưu JTI + expiryTime vào Redis với key: "blacklist:jti:{jti}"
            // 3. Set TTL = expiryTime - now (để tự động xóa khi token hết hạn)
            // 4. Khi verify token, check xem JTI có trong blacklist không
        } catch (AppException e) {
            // Token không hợp lệ (đã hết hạn, chữ ký sai...)
            // Không cần làm gì, chỉ log để tracking
            // User có thể đã logout rồi hoặc token đã hết hạn tự nhiên
            log.info("Token already invalid during logout, no action needed");
        }
    }

    /**
     * Xác thực JWT token (verify signature và expiration)
     * 
     * Phương thức private này được dùng bởi introspect() và logout() để verify token.
     * Spring Security cũng có JwtDecoder riêng để verify token trong filter chain.
     * 
     * Quy trình verify:
     * 1. Parse token string thành SignedJWT object
     * 2. Tạo MACVerifier với SIGNER_KEY để verify chữ ký
     * 3. Verify chữ ký của token (đảm bảo token không bị giả mạo)
     * 4. Kiểm tra thời gian hết hạn (exp claim) - token phải chưa hết hạn
     * 
     * Token được coi là hợp lệ khi:
     * - Chữ ký đúng (verified = true) - đảm bảo token được tạo bởi server này
     * - Chưa hết hạn (expiryTime > now) - đảm bảo token còn hiệu lực
     * 
     * @param token - JWT token string cần verify (format: "eyJhbGciOiJIUzUxMiJ9...")
     * @return SignedJWT - Object đã được verify, chứa claims của token
     * @throws AppException với ErrorCode.UNAUTHENTICATED nếu token không hợp lệ
     * @throws JOSEException - Lỗi liên quan đến JWT/JOSE (ví dụ: parse error, verify error)
     * @throws ParseException - Lỗi parse token string
     * 
     * Lưu ý: Method này chỉ verify signature và expiration, không check blacklist
     */
    private SignedJWT verifyToken(String token) throws JOSEException, ParseException {
        // Tạo MACVerifier để verify chữ ký
        // MACVerifier sử dụng cùng secret key (SIGNER_KEY) để verify
        // Nếu chữ ký không khớp, verify() sẽ return false
        JWSVerifier verifier = new MACVerifier(SIGNER_KEY.getBytes());

        // Parse token string thành SignedJWT object
        // Nếu format không đúng, sẽ throw ParseException
        SignedJWT signedJWT = SignedJWT.parse(token);

        // Lấy thời gian hết hạn từ claims (exp claim)
        Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();

        // Verify chữ ký của token
        // verified = true nếu chữ ký đúng, false nếu sai
        var verified = signedJWT.verify(verifier);

        // Token hợp lệ khi: Chữ ký đúng VÀ chưa hết hạn
        // Nếu một trong hai điều kiện không thỏa mãn → throw exception
        if (!(verified && expiryTime.after(new Date()))) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        // Token hợp lệ, return SignedJWT object (có thể extract claims từ đây)
        return signedJWT;
    }

    /**
     * Tạo JWT Token mới cho user đã đăng nhập thành công
     * 
     * Phương thức này tạo ra JWT token chứa thông tin user và quyền truy cập.
     * Token này sẽ được client sử dụng để xác thực cho các request tiếp theo.
     * 
     * JWT Token Structure:
     * 
     * Header:
     * - alg: HS512 (HMAC with SHA-512) - Thuật toán ký
     * - typ: JWT
     * 
     * Payload (Claims):
     * - sub: Email của user (subject) - Dùng làm định danh user
     * - iss: "swp391.com" (issuer) - Người phát hành token
     * - iat: Thời điểm phát hành token (issued at)
     * - exp: Thời điểm hết hạn (expiration) - Hiện tại: 2 giờ sau khi phát hành
     * - userId: ID của user (custom claim)
     * - scope: Role của user (SinhVien, QuanTriVien, ChuTich...) - Dùng để authorization
     * - clubIds: Danh sách ID các CLB mà user là leader/member (custom claim)
     * 
     * Signature:
     * - Được ký bằng SIGNER_KEY (secret key) sử dụng HS512 algorithm
     * - Đảm bảo token không bị giả mạo
     * 
     * @param user - User entity đã được xác thực (đăng nhập thành công)
     * @return String - JWT token string (format: "header.payload.signature")
     * @throws RuntimeException - Nếu có lỗi khi ký token (JOSEException)
     * 
     * Lưu ý:
     * - Token có thời hạn 2 giờ (có thể config thay đổi)
     * - Token chứa thông tin nhạy cảm (userId, email, role), không nên log ra
     * - Secret key (SIGNER_KEY) phải được bảo mật, không commit vào git
     */
    private String generateToken(Users user) {
        // ========== BƯỚC 1: TẠO HEADER ==========
        // Header chứa thông tin về thuật toán ký (HS512)
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        // ========== BƯỚC 2: TẠO PAYLOAD (CLAIMS) ==========
        // Payload chứa tất cả thông tin về user và quyền truy cập
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                // Subject (sub): Email của user - dùng làm định danh chính
                // Spring Security sẽ dùng getAuthentication().getName() để lấy email từ đây
                .subject(user.getEmail())
                
                // Issuer (iss): Người phát hành token (tên domain/application)
                .issuer("swp391.com")
                
                // Issued At (iat): Thời điểm phát hành token
                .issueTime(new Date())
                
                // Expiration Time (exp): Thời điểm hết hạn - 2 giờ sau khi phát hành
                // Token sẽ không còn hợp lệ sau thời điểm này
                .expirationTime(new Date(
                        Instant.now().plus(2, ChronoUnit.HOURS).toEpochMilli()
                ))
                
                // Custom claim: userId - ID của user (UUID)
                .claim("userId", user.getUserId())
                
                // Custom claim: scope - Role của user (SinhVien, QuanTriVien, ChuTich...)
                // Spring Security sẽ convert thành Authority: "SCOPE_{roleName}"
                // Ví dụ: scope = "QuanTriVien" → Authority = "SCOPE_QuanTriVien"
                .claim("scope", buildScope(user))
                
                // Custom claim: clubIds - Danh sách ID các CLB mà user là leader hoặc member
                // Dùng để check quyền truy cập các resource của CLB cụ thể
                .claim("clubIds", getClubIds(user))
                .build();

        // Tạo Payload object từ claims set (chuyển sang JSON format)
        Payload payload = new Payload(jwtClaimsSet.toJSONObject());

        // Tạo JWSObject (JWT object) từ header và payload
        JWSObject jwsObject = new JWSObject(header, payload);

        // ========== BƯỚC 3: KÝ TOKEN ==========
        try {
            // Ký token bằng MACSigner với secret key (SIGNER_KEY)
            // Signature được tạo từ: Base64Url(header) + "." + Base64Url(payload) + secret_key
            // Algorithm: HMAC SHA-512
            jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes()));
            
            // Serialize token thành string format: "header.payload.signature"
            // Đây là token cuối cùng được gửi cho client
            return jwsObject.serialize();
        } catch (JOSEException e) {
            // Lỗi khi ký token (ví dụ: secret key không hợp lệ)
            log.error("Cannot create token for user: {}", user.getEmail(), e);
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }

    /**
     * Tạo chuỗi scope từ Role của user
     * 
     * Scope được dùng để authorization (phân quyền) trong Spring Security.
     * Spring Security sẽ tự động thêm prefix "SCOPE_" vào scope để tạo Authority.
     * 
     * Ví dụ:
     * - Role = SinhVien → scope = "SinhVien" → Authority = "SCOPE_SinhVien"
     * - Role = QuanTriVien → scope = "QuanTriVien" → Authority = "SCOPE_QuanTriVien"
     * - Role = ChuTich → scope = "ChuTich" → Authority = "SCOPE_ChuTich"
     * 
     * Lưu ý:
     * - Không thêm prefix "ROLE_" ở đây vì Spring Security sẽ tự động thêm "SCOPE_"
     * - Nếu cần dùng @PreAuthorize, sử dụng: hasAuthority('SCOPE_QuanTriVien')
     * 
     * @param user - User entity cần lấy scope
     * @return String - Tên role dạng string (ví dụ: "SinhVien", "QuanTriVien")
     *                  Nếu user không có role, trả về empty string
     */
    private String buildScope(Users user) {
        // Kiểm tra user có role không
        if (user.getRole() != null) {
            // Lấy tên role từ enum (ví dụ: RoleType.SinhVien → "SinhVien")
            return user.getRole().getRoleName().name();
        }
        // Trường hợp user không có role (không nên xảy ra trong thực tế)
        return "";
    }
    
    /**
     * Lấy danh sách clubId mà user là leader hoặc member
     * 
     * Phương thức này thu thập tất cả CLB mà user có quyền truy cập:
     * 1. CLB mà user là founder (người sáng lập)
     * 2. CLB mà user là member/leader (đã được duyệt và đã thanh toán)
     * 
     * Kết quả được thêm vào JWT token như một custom claim để:
     * - Check quyền truy cập resource của CLB cụ thể
     * - Tránh phải query database mỗi lần check permission
     * - Cải thiện performance
     * 
     * Lưu ý: Danh sách clubIds trong token có thể không real-time nếu user join/leave CLB
     *        User cần đăng nhập lại để token được cập nhật với clubIds mới
     * 
     * @param user - User entity cần lấy danh sách clubIds
     * @return List<Integer> - Danh sách clubId (đã loại bỏ trùng lặp)
     */
    private List<Integer> getClubIds(Users user) {
        // ========== BƯỚC 1: LẤY CLB MÀ USER LÀ FOUNDER ==========
        // Founder là người sáng lập CLB, có quyền cao nhất trong CLB đó
        List<Integer> foundedClubIds = clubRepository.findByFounder(user).stream()
                .map(club -> club.getClubId())  // Extract clubId
                .collect(Collectors.toList());
        
        // ========== BƯỚC 2: LẤY CLB MÀ USER LÀ MEMBER/LEADER ==========
        // Tìm tất cả đăng ký (Registers) của user, lọc những đăng ký đã được duyệt và đã thanh toán
        List<Integer> memberClubIds = registerRepository.findByUser(user).stream()
                // Chỉ lấy đăng ký đã được duyệt (DaDuyet) và đã thanh toán (isPaid = true)
                // Đây là điều kiện để user được coi là "active member" của CLB
                .filter(reg -> reg.getStatus() == JoinStatus.DaDuyet && reg.getIsPaid())
                // Đảm bảo membershipPackage và club không null (tránh NullPointerException)
                .filter(reg -> reg.getMembershipPackage() != null && reg.getMembershipPackage().getClub() != null)
                // Extract clubId từ membershipPackage.club
                .map(reg -> reg.getMembershipPackage().getClub().getClubId())
                // Loại bỏ trùng lặp (user có thể có nhiều đăng ký trong cùng 1 CLB với các package khác nhau)
                .distinct()
                .collect(Collectors.toList());
        
        // ========== BƯỚC 3: GỘP VÀ LOẠI BỎ TRÙNG LẶP ==========
        // Gộp 2 danh sách lại
        foundedClubIds.addAll(memberClubIds);
        
        // Loại bỏ trùng lặp một lần nữa (nếu user vừa là founder vừa là member của cùng 1 CLB)
        // Trả về danh sách clubId cuối cùng (không trùng lặp)
        return foundedClubIds.stream()
                .distinct()
                .collect(Collectors.toList());
    }
}

