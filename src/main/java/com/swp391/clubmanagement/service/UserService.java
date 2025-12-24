// Package định nghĩa service layer - xử lý business logic cho quản lý người dùng
package com.swp391.clubmanagement.service;

// ========== DTO ==========
import com.swp391.clubmanagement.dto.request.*; // Tất cả request DTOs
import com.swp391.clubmanagement.dto.response.UserResponse; // Response DTO cho user

// ========== Entity ==========
import com.swp391.clubmanagement.entity.Roles; // Entity vai trò trong hệ thống
import com.swp391.clubmanagement.entity.Users; // Entity người dùng

// ========== Enum ==========
import com.swp391.clubmanagement.enums.RoleType; // Vai trò hệ thống: Student, Admin, ChuTich

// ========== Exception ==========
import com.swp391.clubmanagement.exception.AppException; // Custom exception
import com.swp391.clubmanagement.exception.ErrorCode; // Mã lỗi hệ thống

// ========== Mapper ==========
import com.swp391.clubmanagement.mapper.UserMapper; // Chuyển đổi Entity <-> DTO

// ========== Repository ==========
import com.swp391.clubmanagement.repository.RoleRepository; // Repository cho bảng Roles
import com.swp391.clubmanagement.repository.UserRepository; // Repository cho bảng Users

// ========== Utilities ==========
import com.swp391.clubmanagement.utils.DateTimeUtils; // Xử lý thời gian theo múi giờ VN

// ========== Lombok ==========
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor; // Tự động tạo constructor inject dependencies
import lombok.experimental.FieldDefaults; // Tự động thêm private final cho fields
import lombok.experimental.NonFinal; // Cho phép field không final
import org.springframework.beans.factory.annotation.Value; // Inject giá trị từ config

// ========== Spring Framework ==========
import org.springframework.security.core.context.SecurityContextHolder; // Lấy user hiện tại từ JWT
import org.springframework.security.crypto.password.PasswordEncoder; // Mã hóa mật khẩu (BCrypt)
import org.springframework.stereotype.Service; // Đánh dấu class là Spring Service Bean
import org.springframework.data.domain.Page; // Phân trang
import org.springframework.data.domain.Pageable; // Thông tin phân trang
import org.springframework.transaction.annotation.Transactional; // Quản lý transaction

// ========== Java Standard Library ==========
import java.util.UUID; // Tạo mã xác thực ngẫu nhiên

/**
 * Service quản lý người dùng
 * 
 * Chức năng chính:
 * - Đăng ký tài khoản mới (tạo user, gửi email xác thực)
 * - Xác thực email (verify email bằng token hoặc mã)
 * - Quên mật khẩu (reset password, gửi email)
 * - Đổi mật khẩu (yêu cầu đăng nhập)
 * - Xem/cập nhật thông tin cá nhân
 * - Xem danh sách user (Admin)
 * - Xóa user (Admin, soft delete)
 * 
 * @Service: Spring Service Bean, được quản lý bởi IoC Container
 * @RequiredArgsConstructor: Lombok tự động tạo constructor inject dependencies
 * @FieldDefaults: Tự động thêm private final cho các field
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserService {
    /** Repository thao tác với bảng users */
    UserRepository userRepository;
    
    /** Repository thao tác với bảng roles */
    RoleRepository roleRepository;
    
    /** Mapper chuyển đổi Entity (Users) <-> DTO (UserResponse) */
    UserMapper userMapper;
    
    /** PasswordEncoder để mã hóa mật khẩu (BCrypt) */
    PasswordEncoder passwordEncoder;
    
    /** Service gửi email (xác thực, quên mật khẩu) */
    EmailService emailService;

    /** Base URL của ứng dụng (để tạo link xác thực email) */
    @NonFinal
    @Value("${app.base-url:https://clubmanage.azurewebsites.net/api}")
    String baseUrl;

    /** Thời hạn link xác thực email (1 giờ) */
    private static final int VERIFICATION_EXPIRY_HOURS = 1;

    /**
     * Tạo tài khoản người dùng mới
     * 
     * Business Rules:
     * - Email và mã sinh viên phải unique
     * - Mật khẩu được mã hóa bằng BCrypt
     * - Role mặc định là SinhVien (Student)
     * - Tạo mã xác thực ngẫu nhiên (UUID)
     * - Gửi email xác thực với link (có thời hạn 1 giờ)
     * - Tài khoản chưa được kích hoạt (enabled = false) cho đến khi verify email
     * 
     * @param request Thông tin đăng ký: email, password, fullName, studentCode, etc.
     * @return Users Entity đã tạo
     * @throws AppException USER_EXISTED nếu email hoặc studentCode đã tồn tại
     */
    public Users createUser(UserCreationRequest request) {
        // Kiểm tra email đã tồn tại chưa
        if (userRepository.existsByEmail(request.getEmail()))
            throw new AppException(ErrorCode.USER_EXISTED);

        // Kiểm tra mã sinh viên đã tồn tại chưa
        if (userRepository.existsByStudentCode(request.getStudentCode()))
            throw new AppException(ErrorCode.USER_EXISTED);

        // Chuyển đổi DTO sang Entity
        Users user = userMapper.toUser(request);
        
        // Mã hóa mật khẩu bằng BCrypt trước khi lưu
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        // Gán role mặc định là SinhVien (Student)
        Roles studentRole = roleRepository.findByRoleName(RoleType.SinhVien)
                .orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        user.setRole(studentRole);

        // Tạo mã xác nhận ngẫu nhiên (UUID) và thời hạn
        String verificationToken = UUID.randomUUID().toString();
        user.setVerificationCode(verificationToken);
        user.setVerificationExpiry(DateTimeUtils.nowVietnam().plusHours(VERIFICATION_EXPIRY_HOURS));
        user.setEnabled(false); // Chưa kích hoạt cho đến khi verify email

        userRepository.save(user);

        // Gửi email xác thực với link (link có chứa token)
        String verificationLink = baseUrl + "/users/verify?token=" + verificationToken;
        emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), verificationLink);

        return user;
    }

    /**
     * Xác thực email thông qua token (click link trong email)
     * 
     * Flow:
     * 1. Tìm user theo verificationCode (token)
     * 2. Kiểm tra đã xác thực chưa (nếu rồi thì return luôn)
     * 3. Kiểm tra link còn hạn không (1 giờ)
     * 4. Kích hoạt tài khoản (enabled = true) và xóa verificationCode
     * 
     * @param token Mã xác thực từ link trong email
     * @throws AppException INVALID_VERIFICATION_CODE nếu token không hợp lệ
     * @throws AppException VERIFICATION_LINK_EXPIRED nếu link đã hết hạn
     */
    public void verifyEmailByToken(String token) {
        // Tìm user theo verificationCode
        Users user = userRepository.findByVerificationCode(token)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_VERIFICATION_CODE));

        // Kiểm tra đã xác thực chưa (nếu rồi thì không cần làm gì)
        if (user.isEnabled()) {
            return;
        }

        // Kiểm tra link còn hạn không (so sánh với thời gian hiện tại)
        if (user.getVerificationExpiry() == null || 
            DateTimeUtils.nowVietnam().isAfter(user.getVerificationExpiry())) {
            throw new AppException(ErrorCode.VERIFICATION_LINK_EXPIRED);
        }

        // Kích hoạt tài khoản và xóa thông tin xác thực
        user.setEnabled(true);
        user.setVerificationCode(null);
        user.setVerificationExpiry(null);
        userRepository.save(user);
    }

    /**
     * Xác thực email bằng mã thủ công (giữ lại cho backward compatibility)
     */
    public void verifyEmail(VerifyEmailRequest request) {
        Users user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.isEnabled()) {
            return;
        }

        // Kiểm tra link còn hạn không
        if (user.getVerificationExpiry() == null ||
            DateTimeUtils.nowVietnam().isAfter(user.getVerificationExpiry())) {
            throw new AppException(ErrorCode.VERIFICATION_LINK_EXPIRED);
        }

        if (request.getVerificationCode().equals(user.getVerificationCode())) {
            user.setEnabled(true);
            user.setVerificationCode(null);
            user.setVerificationExpiry(null);
            userRepository.save(user);
        } else {
            throw new AppException(ErrorCode.INVALID_VERIFICATION_CODE);
        }
    }

    /**
     * Quên mật khẩu - Reset mật khẩu và gửi email
     * 
     * Flow:
     * 1. Tìm user theo email
     * 2. Tạo mật khẩu mới ngẫu nhiên (8 ký tự)
     * 3. Mã hóa và lưu mật khẩu mới
     * 4. Gửi email chứa mật khẩu mới
     * 
     * @param request Chứa email của user
     * @throws AppException USER_NOT_FOUND nếu không tìm thấy user
     */
    public void forgotPassword(ForgotPasswordRequest request) {
        // Tìm user theo email
        Users user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Tạo mật khẩu mới ngẫu nhiên (8 ký tự đầu của UUID)
        String newPassword = UUID.randomUUID().toString().substring(0, 8);
        
        // Mã hóa và lưu mật khẩu mới
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Gửi email chứa mật khẩu mới
        emailService.sendForgotPasswordEmail(user.getEmail(), user.getFullName(), newPassword);
    }

    /**
     * Đổi mật khẩu (Yêu cầu User phải đang đăng nhập)
     * 
     * Flow:
     * 1. Lấy thông tin user đang đăng nhập từ JWT token
     * 2. Kiểm tra mật khẩu cũ có đúng không
     * 3. Mã hóa và lưu mật khẩu mới
     * 
     * @param request Chứa oldPassword và newPassword
     * @throws AppException WRONG_PASSWORD nếu mật khẩu cũ không đúng
     */
    public void changePassword(ChangePasswordRequest request) {
        // Lấy thông tin user đang đăng nhập từ JWT token
        Users user = getMyInfo();

        // Kiểm tra mật khẩu cũ có đúng không (so sánh với BCrypt hash)
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.WRONG_PASSWORD);
        }

        // Mã hóa và lưu mật khẩu mới
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    /**
     * Lấy thông tin user hiện tại từ JWT token
     * 
     * @return Users Entity của user hiện tại
     * @throws AppException USER_NOT_FOUND nếu không tìm thấy user
     */
    public Users getMyInfo() {
        // Lấy email từ JWT token (subject)
        var context = SecurityContextHolder.getContext();
        String email = context.getAuthentication().getName();
        
        // Tìm user trong database
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * Lấy thông tin cá nhân của user hiện tại (dạng UserResponse với clubIds)
     */
    public UserResponse getMyInfoResponse() {
        Users user = getMyInfo();
        return userMapper.toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        // Sử dụng findAllWithRegisters để eager load registers và populate clubIds
        return userRepository.findAllWithRegisters(pageable)
                .map(userMapper::toUserResponse);
    }

    /**
     * Xóa user (Admin only) - Soft delete
     * Không xóa user đang là founder của CLB
     */
    public void deleteUser(String userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Kiểm tra user có phải founder của CLB nào không
        if (user.getFoundedClubs() != null && !user.getFoundedClubs().isEmpty()) {
            throw new AppException(ErrorCode.CANNOT_DELETE_FOUNDER);
        }

        // Soft delete: set is_active = false
        user.setIsActive(false);
        userRepository.save(user);
    }

    public Users updateUser(UserUpdateRequest request) {
        Users user = getMyInfo();
        userMapper.updateUser(user, request);
        return userRepository.save(user);
    }
}
