package com.swp391.clubmanagement.service;

import com.swp391.clubmanagement.dto.request.*;
import com.swp391.clubmanagement.dto.response.UserResponse;
import com.swp391.clubmanagement.entity.Roles;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.enums.RoleType;
import com.swp391.clubmanagement.exception.AppException;
import com.swp391.clubmanagement.exception.ErrorCode;
import com.swp391.clubmanagement.mapper.UserMapper;
import com.swp391.clubmanagement.repository.RoleRepository;
import com.swp391.clubmanagement.repository.UserRepository;
import com.swp391.clubmanagement.utils.DateTimeUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * UserService - Service xử lý logic nghiệp vụ cho quản lý người dùng
 * 
 * Service này chịu trách nhiệm quản lý toàn bộ vòng đời của một user account:
 * - Tạo tài khoản mới với email verification
 * - Xác thực email (qua link hoặc mã)
 * - Quên mật khẩu và reset password
 * - Đổi mật khẩu (khi đã đăng nhập)
 * - Xem/cập nhật thông tin cá nhân
 * - Quản lý danh sách user (Admin)
 * - Xóa user (Admin, soft delete)
 * 
 * Quy trình đăng ký tài khoản:
 * 1. User tạo account với email, password, thông tin cá nhân
 * 2. Hệ thống tạo verification token (UUID) và set expiry time (1 giờ)
 * 3. Gửi email xác thực với link chứa token
 * 4. User click link → verifyEmailByToken() → enabled = true
 * 5. User có thể đăng nhập sau khi đã verify email
 * 
 * @Service - Đánh dấu đây là một Spring Service, được quản lý bởi Spring Container
 * @RequiredArgsConstructor - Lombok tự động tạo constructor với các field final để dependency injection
 * @FieldDefaults - Lombok: tất cả field là PRIVATE và FINAL (immutable dependencies)
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserService {
    /**
     * Repository để truy vấn và thao tác với bảng Users trong database
     * Chứa thông tin tài khoản người dùng (email, password, fullName, studentCode, role...)
     */
    UserRepository userRepository;
    
    /**
     * Repository để truy vấn và thao tác với bảng Roles trong database
     * Chứa các role của hệ thống (SinhVien, Admin, ChuTich, PhoChuTich...)
     */
    RoleRepository roleRepository;
    
    /**
     * Mapper để chuyển đổi giữa Entity (Users) và DTO (UserResponse, UserCreationRequest...)
     * Giúp tách biệt layer, không expose entity trực tiếp ra controller
     */
    UserMapper userMapper;
    
    /**
     * PasswordEncoder để mã hóa mật khẩu trước khi lưu vào database
     * Sử dụng BCrypt algorithm (default của Spring Security)
     * Mật khẩu được hash, không thể reverse để lấy lại password gốc
     */
    PasswordEncoder passwordEncoder;
    
    /**
     * EmailService để gửi email xác thực và email quên mật khẩu
     * Tách biệt logic gửi email ra service riêng để dễ maintain và test
     */
    EmailService emailService;

    /**
     * Base URL của ứng dụng để tạo verification link
     * Được inject từ application.yaml qua @Value annotation
     * Default value: https://clubmanage.azurewebsites.net/api (production URL)
     * Có thể override trong config file cho các environment khác (dev, staging...)
     * 
     * @NonFinal - Field này không phải final vì cần có thể inject giá trị từ config
     */
    @NonFinal
    @Value("${app.base-url:https://clubmanage.azurewebsites.net/api}")
    String baseUrl;

    /**
     * Thời hạn link xác thực email (tính bằng giờ)
     * Link xác thực chỉ có hiệu lực trong 1 giờ sau khi được tạo
     * Sau thời gian này, user phải yêu cầu gửi lại email xác thực
     * 
     * Lý do: Bảo mật - giới hạn thời gian sử dụng token để giảm rủi ro nếu token bị lộ
     */
    private static final int VERIFICATION_EXPIRY_HOURS = 1;

    /**
     * Tạo tài khoản người dùng mới (Đăng ký)
     * 
     * Phương thức này xử lý quá trình đăng ký tài khoản mới:
     * 1. Validate email và studentCode không trùng lặp
     * 2. Tạo user mới với role mặc định là SinhVien
     * 3. Mã hóa mật khẩu bằng BCrypt
     * 4. Tạo verification token và set expiry time
     * 5. Set enabled = false (chưa được verify email)
     * 6. Gửi email xác thực với link
     * 
     * @param request - DTO chứa thông tin đăng ký (email, password, fullName, studentCode, phone...)
     * @return Users - Entity user vừa được tạo
     * @throws AppException với ErrorCode.USER_EXISTED nếu email hoặc studentCode đã tồn tại
     * @throws AppException với ErrorCode.UNCATEGORIZED_EXCEPTION nếu không tìm thấy role SinhVien
     * 
     * Lưu ý: User chưa thể đăng nhập ngay, phải verify email trước (enabled = true)
     */
    public Users createUser(UserCreationRequest request) {
        // Kiểm tra email đã tồn tại chưa (business rule: email phải unique)
        // Nếu đã tồn tại, throw exception với error code phù hợp
        if (userRepository.existsByEmail(request.getEmail()))
            throw new AppException(ErrorCode.USER_EXISTED);

        // Kiểm tra studentCode đã tồn tại chưa (business rule: mã sinh viên phải unique)
        // Mỗi sinh viên chỉ có thể có 1 tài khoản
        if (userRepository.existsByStudentCode(request.getStudentCode()))
            throw new AppException(ErrorCode.USER_EXISTED);

        // Map từ DTO (UserCreationRequest) sang Entity (Users)
        // Mapper sẽ copy các field tương ứng từ request sang user entity
        Users user = userMapper.toUser(request);
        
        // Mã hóa mật khẩu bằng BCrypt trước khi lưu vào database
        // BCrypt là one-way hash, không thể reverse để lấy lại password gốc
        // Mỗi lần hash sẽ tạo ra một chuỗi khác nhau (do salt random), nhưng vẫn verify được
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        // Set role mặc định cho user mới là SinhVien
        // Role này xác định quyền hạn của user trong hệ thống
        // Có thể thay đổi sau khi user thành lập CLB (thành ChuTich) hoặc được Admin promote
        Roles studentRole = roleRepository.findByRoleName(RoleType.SinhVien)
                .orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        user.setRole(studentRole);

        // Tạo verification token (UUID ngẫu nhiên) để xác thực email
        // Token này sẽ được gửi trong link xác thực email
        // UUID đảm bảo tính unique và khó đoán (bảo mật)
        String verificationToken = UUID.randomUUID().toString();
        user.setVerificationCode(verificationToken);
        
        // Set thời hạn link xác thực: hiện tại + 1 giờ
        // Sau thời gian này, link sẽ hết hạn và user phải yêu cầu gửi lại email
        user.setVerificationExpiry(DateTimeUtils.nowVietnam().plusHours(VERIFICATION_EXPIRY_HOURS));
        
        // User mới chưa được verify email nên enabled = false
        // User chỉ có thể đăng nhập sau khi click link xác thực (enabled = true)
        user.setEnabled(false);

        // Lưu user vào database
        userRepository.save(user);

        // Tạo link xác thực: baseUrl + endpoint + token
        // Ví dụ: https://clubmanage.azurewebsites.net/api/users/verify?token=abc123...
        String verificationLink = baseUrl + "/users/verify?token=" + verificationToken;
        
        // Gửi email xác thực với link
        // EmailService sẽ format HTML email đẹp và gửi đi
        emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), verificationLink);

        // Trả về user entity (có thể dùng để test hoặc log)
        return user;
    }

    /**
     * Xác thực email thông qua token (click link trong email)
     * 
     * Phương thức này được gọi khi user click vào link xác thực trong email.
     * Link có dạng: /users/verify?token=abc123...
     * 
     * Quy trình:
     * 1. Tìm user có verificationCode trùng với token trong link
     * 2. Kiểm tra user đã được verify chưa (nếu rồi thì return, không làm gì)
     * 3. Kiểm tra link còn hạn không (không quá 1 giờ)
     * 4. Nếu hợp lệ: set enabled = true, xóa verificationCode và verificationExpiry
     * 5. User giờ có thể đăng nhập
     * 
     * @param token - Verification token từ link trong email (UUID string)
     * @throws AppException với ErrorCode.INVALID_VERIFICATION_CODE nếu token không hợp lệ
     * @throws AppException với ErrorCode.VERIFICATION_LINK_EXPIRED nếu link đã hết hạn
     * 
     * Lưu ý: Nếu user đã verify rồi, method này sẽ return ngay (idempotent - có thể gọi nhiều lần an toàn)
     */
    public void verifyEmailByToken(String token) {
        // Tìm user có verificationCode trùng với token
        // Nếu không tìm thấy, có thể token không hợp lệ hoặc đã được dùng
        Users user = userRepository.findByVerificationCode(token)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_VERIFICATION_CODE));

        // Kiểm tra user đã được xác thực chưa (enabled = true)
        // Nếu đã verify rồi, không cần làm gì thêm (idempotent operation)
        // Có thể user click link nhiều lần hoặc đã verify bằng cách khác
        if (user.isEnabled()) {
            return;  // Return ngay, không cần xử lý gì thêm
        }

        // Kiểm tra link xác thực còn hạn không
        // Link chỉ có hiệu lực trong 1 giờ sau khi được tạo
        // Nếu đã quá thời hạn, user phải yêu cầu gửi lại email xác thực
        if (user.getVerificationExpiry() == null || 
            DateTimeUtils.nowVietnam().isAfter(user.getVerificationExpiry())) {
            throw new AppException(ErrorCode.VERIFICATION_LINK_EXPIRED);
        }

        // Xác thực thành công: kích hoạt tài khoản
        user.setEnabled(true);                // Cho phép user đăng nhập
        user.setVerificationCode(null);       // Xóa token (không cần dùng nữa, bảo mật)
        user.setVerificationExpiry(null);     // Xóa expiry time (không cần dùng nữa)
        
        // Lưu thay đổi vào database
        userRepository.save(user);
    }

    /**
     * Xác thực email bằng mã thủ công (giữ lại cho backward compatibility)
     * 
     * Phương thức này cho phép user nhập mã xác thực thủ công thay vì click link.
     * Đây là phương thức cũ, có thể được dùng nếu cần support cả 2 cách xác thực.
     * 
     * Quy trình:
     * 1. Tìm user theo email
     * 2. Kiểm tra user chưa được verify
     * 3. Kiểm tra mã xác thực còn hạn
     * 4. So sánh mã trong request với mã trong database
     * 5. Nếu khớp: kích hoạt tài khoản
     * 
     * @param request - DTO chứa email và verificationCode (mã xác thực)
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user
     * @throws AppException với ErrorCode.VERIFICATION_LINK_EXPIRED nếu mã đã hết hạn
     * @throws AppException với ErrorCode.INVALID_VERIFICATION_CODE nếu mã không đúng
     * 
     * Lưu ý: Phương thức này có thể không còn được sử dụng nếu chỉ dùng link xác thực
     *        Nhưng được giữ lại để đảm bảo backward compatibility với code/API cũ
     */
    public void verifyEmail(VerifyEmailRequest request) {
        // Tìm user theo email (khác với verifyEmailByToken là tìm theo token)
        Users user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Kiểm tra user đã được xác thực chưa
        // Nếu đã verify rồi, return ngay (idempotent)
        if (user.isEnabled()) {
            return;
        }

        // Kiểm tra mã xác thực còn hạn không (giống như verifyEmailByToken)
        // Link/mã chỉ có hiệu lực trong 1 giờ
        if (user.getVerificationExpiry() == null ||
            DateTimeUtils.nowVietnam().isAfter(user.getVerificationExpiry())) {
            throw new AppException(ErrorCode.VERIFICATION_LINK_EXPIRED);
        }

        // So sánh mã xác thực trong request với mã lưu trong database
        // Sử dụng equals() để so sánh string (an toàn, tránh NullPointerException)
        if (request.getVerificationCode().equals(user.getVerificationCode())) {
            // Mã đúng: kích hoạt tài khoản
            user.setEnabled(true);
            user.setVerificationCode(null);       // Xóa mã (bảo mật)
            user.setVerificationExpiry(null);     // Xóa expiry time
            userRepository.save(user);
        } else {
            // Mã sai: throw exception
            throw new AppException(ErrorCode.INVALID_VERIFICATION_CODE);
        }
    }

    /**
     * Quên mật khẩu - Reset mật khẩu và gửi mật khẩu mới qua email
     * 
     * Phương thức này xử lý quy trình "Forgot Password":
     * 1. User nhập email vào form "Quên mật khẩu"
     * 2. Hệ thống tạo mật khẩu mới ngẫu nhiên (8 ký tự)
     * 3. Mã hóa và lưu mật khẩu mới vào database
     * 4. Gửi email chứa mật khẩu mới cho user
     * 5. User đăng nhập bằng mật khẩu mới, sau đó nên đổi mật khẩu
     * 
     * @param request - DTO chứa email của user cần reset password
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user với email đó
     * 
     * Lưu ý bảo mật:
     * - Mật khẩu mới được generate ngẫu nhiên (UUID substring) để đảm bảo an toàn
     * - Mật khẩu được gửi qua email (có rủi ro bảo mật, nhưng phổ biến trong thực tế)
     * - User nên đổi mật khẩu ngay sau khi đăng nhập (được nhắc trong email)
     * - Trong production, có thể cải thiện bằng cách dùng reset token + link thay vì gửi password trực tiếp
     */
    public void forgotPassword(ForgotPasswordRequest request) {
        // Tìm user theo email
        Users user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Tạo mật khẩu mới ngẫu nhiên: lấy 8 ký tự đầu của UUID
        // UUID có format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        // substring(0, 8) lấy 8 ký tự đầu (ví dụ: "a1b2c3d4")
        // Lý do dùng UUID: đảm bảo tính ngẫu nhiên và khó đoán
        String newPassword = UUID.randomUUID().toString().substring(0, 8);
        
        // Mã hóa mật khẩu mới bằng BCrypt trước khi lưu vào database
        // Không bao giờ lưu plain text password
        user.setPassword(passwordEncoder.encode(newPassword));
        
        // Lưu user với mật khẩu mới
        userRepository.save(user);

        // Gửi email chứa mật khẩu mới cho user
        // Email sẽ có format đẹp và nhắc user đổi mật khẩu ngay sau khi đăng nhập
        emailService.sendForgotPasswordEmail(user.getEmail(), user.getFullName(), newPassword);
    }

    /**
     * Đổi mật khẩu (Yêu cầu User phải đang đăng nhập)
     * 
     * Phương thức này cho phép user đổi mật khẩu khi đã đăng nhập vào hệ thống.
     * Đây là cách an toàn và phổ biến để user tự quản lý mật khẩu của mình.
     * 
     * Quy trình:
     * 1. Lấy thông tin user hiện tại từ JWT token (đảm bảo chỉ user đang login mới đổi được)
     * 2. Xác thực mật khẩu cũ có đúng không (bằng BCrypt matching)
     * 3. Nếu đúng: mã hóa và lưu mật khẩu mới
     * 4. Nếu sai: throw exception
     * 
     * @param request - DTO chứa oldPassword và newPassword
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user (từ getMyInfo)
     * @throws AppException với ErrorCode.WRONG_PASSWORD nếu mật khẩu cũ không đúng
     * 
     * Lưu ý bảo mật:
     * - User phải nhập đúng mật khẩu cũ để đổi (prevent unauthorized access)
     * - Chỉ user đang login mới có thể đổi mật khẩu của chính họ (xác định qua JWT token)
     * - Mật khẩu mới được mã hóa bằng BCrypt trước khi lưu
     */
    public void changePassword(ChangePasswordRequest request) {
        // Bước 1: Lấy thông tin user đang đăng nhập từ Security Context
        // getMyInfo() sẽ lấy email từ JWT token và tìm user tương ứng
        // Đảm bảo user chỉ có thể đổi mật khẩu của chính mình, không thể đổi của user khác
        Users user = getMyInfo();

        // Bước 2: Kiểm tra mật khẩu cũ có đúng không
        // passwordEncoder.matches() sẽ hash oldPassword và so sánh với hash trong database
        // BCrypt matching là secure comparison, không thể reverse
        // Nếu mật khẩu cũ không đúng, throw exception
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.WRONG_PASSWORD);
        }

        // Bước 3: Mật khẩu cũ đúng, tiến hành đổi mật khẩu mới
        // Mã hóa mật khẩu mới bằng BCrypt trước khi lưu
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        
        // Lưu user với mật khẩu mới vào database
        userRepository.save(user);
    }

    /**
     * Lấy thông tin user hiện tại (Entity) từ JWT token
     * 
     * Phương thức helper này lấy thông tin user đang đăng nhập từ Security Context.
     * Security Context chứa thông tin authentication từ JWT token trong request header.
     * 
     * @return Users - Entity user hiện tại
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user với email trong token
     * 
     * Lưu ý:
     * - Chỉ hoạt động khi user đã đăng nhập (có valid JWT token)
     * - getAuthentication().getName() trả về subject của JWT, trong trường hợp này là email
     * - Được dùng trong các method khác cần xác định user hiện tại
     */
    public Users getMyInfo() {
        // Lấy Security Context từ Spring Security
        // Context này được populate bởi JWT filter khi request đến
        var context = SecurityContextHolder.getContext();
        
        // Lấy email từ authentication (subject của JWT token)
        String email = context.getAuthentication().getName();
        
        // Tìm user trong database theo email và return
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * Lấy thông tin cá nhân của user hiện tại (dạng UserResponse với clubIds)
     * 
     * Phương thức này tương tự getMyInfo() nhưng trả về DTO (UserResponse) thay vì Entity.
     * UserResponse có thể chứa thông tin bổ sung như danh sách clubIds mà user tham gia.
     * 
     * @return UserResponse - DTO chứa thông tin user hiện tại
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user
     * 
     * Sử dụng: Controller gọi method này để trả về response cho client
     *          Tránh expose Entity trực tiếp (best practice)
     */
    public UserResponse getMyInfoResponse() {
        // Lấy user entity từ database
        Users user = getMyInfo();
        
        // Map sang DTO Response (có thể tính toán thêm các field derived như clubIds)
        return userMapper.toUserResponse(user);
    }

    /**
     * Lấy danh sách tất cả users với phân trang (Admin only)
     * 
     * Phương thức này cho phép Admin xem danh sách tất cả users trong hệ thống với phân trang.
     * Sử dụng findAllWithRegisters để eager load registers và populate clubIds trong response.
     * 
     * @param pageable - Thông tin phân trang (page number, size, sort...)
     *                  Ví dụ: page=0, size=20, sort=createdAt,desc
     * @return Page<UserResponse> - Trang kết quả chứa danh sách users đã được map sang DTO
     * 
     * @Transactional(readOnly = true) - Đánh dấu đây là read-only operation
     *                                   Tối ưu performance vì không cần write lock
     *                                   Hibernate có thể optimize queries
     * 
     * Lưu ý:
     * - Chỉ Admin mới có quyền gọi method này (kiểm tra ở Controller layer)
     * - findAllWithRegisters() sử dụng JOIN FETCH để eager load registers, tránh N+1 query problem
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        // Sử dụng findAllWithRegisters để eager load registers và populate clubIds
        // Method này có thể dùng JOIN FETCH để load registers cùng lúc, tránh N+1 query problem
        // Sau đó map từng user entity sang UserResponse DTO
        return userRepository.findAllWithRegisters(pageable)
                .map(userMapper::toUserResponse);
    }

    /**
     * Xóa user (Admin only) - Soft delete
     * 
     * Phương thức này cho phép Admin xóa (deactivate) một user account.
     * Sử dụng soft delete (set isActive = false) thay vì hard delete để:
     * - Giữ lại dữ liệu lịch sử (audit trail)
     * - Có thể khôi phục nếu cần
     * - Không ảnh hưởng đến các bản ghi liên quan (CLB, Registers, Memberships...)
     * 
     * Business rule: Không được xóa user đang là founder của CLB nào.
     * Lý do: Founder là người quan trọng, xóa có thể gây mất dữ liệu hoặc logic lỗi.
     * 
     * @param userId - ID của user cần xóa
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user
     * @throws AppException với ErrorCode.CANNOT_DELETE_FOUNDER nếu user là founder của CLB
     * 
     * Lưu ý:
     * - Chỉ Admin mới có quyền gọi method này (kiểm tra ở Controller layer)
     * - Soft delete: user vẫn tồn tại trong DB nhưng isActive = false
     * - User bị deactivate sẽ không thể đăng nhập (có thể check trong authentication filter)
     */
    public void deleteUser(String userId) {
        // Tìm user cần xóa trong database
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Kiểm tra business rule: User không được là founder của CLB nào
        // Nếu user đã thành lập CLB (có foundedClubs), không cho phép xóa
        // Lý do: Founder là người quan trọng, xóa có thể gây vấn đề với CLB
        if (user.getFoundedClubs() != null && !user.getFoundedClubs().isEmpty()) {
            throw new AppException(ErrorCode.CANNOT_DELETE_FOUNDER);
        }

        // Soft delete: set isActive = false thay vì xóa hẳn khỏi database
        // Ưu điểm:
        // - Giữ lại dữ liệu lịch sử (các bản ghi liên quan vẫn có thể reference)
        // - Có thể khôi phục nếu cần (set isActive = true lại)
        // - Không cần cascade delete phức tạp
        user.setIsActive(false);
        userRepository.save(user);
    }

    /**
     * Cập nhật thông tin cá nhân của user hiện tại
     * 
     * Phương thức này cho phép user tự cập nhật thông tin cá nhân của mình
     * (ví dụ: fullName, phone, avatar...).
     * 
     * @param request - DTO chứa thông tin cần cập nhật
     * @return Users - Entity user sau khi được cập nhật
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user (từ getMyInfo)
     * 
     * Lưu ý:
     * - User chỉ có thể cập nhật thông tin của chính mình (xác định qua JWT token)
     * - Một số field có thể không được phép update (ví dụ: email, studentCode, role)
     *   Tùy vào business logic, có thể check trong mapper hoặc service
     */
    public Users updateUser(UserUpdateRequest request) {
        // Lấy thông tin user hiện tại (đảm bảo user chỉ update của chính mình)
        Users user = getMyInfo();
        
        // Cập nhật các field từ request vào user entity
        // Mapper sẽ chỉ update các field được phép, bỏ qua các field không được phép (ví dụ: email, role)
        userMapper.updateUser(user, request);
        
        // Lưu user đã được cập nhật vào database
        return userRepository.save(user);
    }
}
