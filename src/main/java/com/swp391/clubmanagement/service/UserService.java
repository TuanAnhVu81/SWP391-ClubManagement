package com.swp391.clubmanagement.service;

import com.swp391.clubmanagement.dto.request.ForgotPasswordRequest;
import com.swp391.clubmanagement.dto.request.UserCreationRequest;
import com.swp391.clubmanagement.dto.request.UserUpdateRequest;
import com.swp391.clubmanagement.dto.request.VerifyEmailRequest;
import com.swp391.clubmanagement.entity.Roles;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.enums.RoleType;
import com.swp391.clubmanagement.exception.AppException;
import com.swp391.clubmanagement.exception.ErrorCode;
import com.swp391.clubmanagement.mapper.UserMapper;
import com.swp391.clubmanagement.repository.RoleRepository;
import com.swp391.clubmanagement.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserService {
    UserRepository userRepository;
    RoleRepository roleRepository;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;
    EmailService emailService;

    @NonFinal
    @Value("${app.base-url:http://localhost:8080/api}")
    String baseUrl;

    // Thời hạn link xác thực (1 giờ)
    private static final int VERIFICATION_EXPIRY_HOURS = 1;

    public Users createUser(UserCreationRequest request) {
        if (userRepository.existsByEmail(request.getEmail()))
            throw new AppException(ErrorCode.USER_EXISTED);

        if (userRepository.existsByStudentCode(request.getStudentCode()))
            throw new AppException(ErrorCode.USER_EXISTED);

        Users user = userMapper.toUser(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        Roles studentRole = roleRepository.findByRoleName(RoleType.SinhVien)
                .orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        user.setRole(studentRole);

        // Tạo mã xác nhận và thời hạn
        String verificationToken = UUID.randomUUID().toString();
        user.setVerificationCode(verificationToken);
        user.setVerificationExpiry(LocalDateTime.now().plusHours(VERIFICATION_EXPIRY_HOURS));
        user.setEnabled(false);

        userRepository.save(user);

        // Gửi email xác thực với link
        String verificationLink = baseUrl + "/users/verify?token=" + verificationToken;
        emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), verificationLink);

        return user;
    }

    /**
     * Xác thực email thông qua token (click link trong email)
     */
    public void verifyEmailByToken(String token) {
        Users user = userRepository.findByVerificationCode(token)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_VERIFICATION_CODE));

        // Kiểm tra đã xác thực chưa
        if (user.isEnabled()) {
            return;
        }

        // Kiểm tra link còn hạn không
        if (user.getVerificationExpiry() == null || 
            LocalDateTime.now().isAfter(user.getVerificationExpiry())) {
            throw new AppException(ErrorCode.VERIFICATION_LINK_EXPIRED);
        }

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
            LocalDateTime.now().isAfter(user.getVerificationExpiry())) {
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

    public void forgotPassword(ForgotPasswordRequest request) {
        Users user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String newPassword = UUID.randomUUID().toString().substring(0, 8);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        emailService.sendForgotPasswordEmail(user.getEmail(), user.getFullName(), newPassword);
    }

    public Users getMyInfo() {
        var context = SecurityContextHolder.getContext();
        String email = context.getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    public Users updateUser(UserUpdateRequest request) {
        Users user = getMyInfo();
        userMapper.updateUser(user, request);
        return userRepository.save(user);
    }
}
