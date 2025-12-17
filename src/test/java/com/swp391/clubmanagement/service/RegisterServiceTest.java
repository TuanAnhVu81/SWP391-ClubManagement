package com.swp391.clubmanagement.service;

import com.swp391.clubmanagement.dto.request.JoinClubRequest;
import com.swp391.clubmanagement.dto.response.RegisterResponse;
import com.swp391.clubmanagement.entity.Clubs;
import com.swp391.clubmanagement.entity.Memberships;
import com.swp391.clubmanagement.entity.Registers;
import com.swp391.clubmanagement.entity.Roles;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.enums.ClubCategory;
import com.swp391.clubmanagement.enums.JoinStatus;
import com.swp391.clubmanagement.enums.RoleType;
import com.swp391.clubmanagement.exception.AppException;
import com.swp391.clubmanagement.exception.ErrorCode;
import com.swp391.clubmanagement.mapper.RegisterMapper;
import com.swp391.clubmanagement.repository.MembershipRepository;
import com.swp391.clubmanagement.repository.RegisterRepository;
import com.swp391.clubmanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho RegisterService - Luồng sinh viên gửi đơn gia nhập CLB
 * 
 * Test Cases:
 * 1. Sinh viên đăng ký tham gia CLB thành công
 * 2. Sinh viên không thể đăng ký CLB đã tham gia
 * 3. Sinh viên không thể đăng ký khi đang chờ duyệt
 * 4. Sinh viên có thể tái gia nhập sau khi bị từ chối
 * 5. Xem danh sách các CLB đã đăng ký
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RegisterService Unit Tests - Luồng tham gia CLB")
class RegisterServiceTest {

    @Mock
    private RegisterRepository registerRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RegisterMapper registerMapper;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private RegisterService registerService;

    private Users testStudent;
    private Clubs testClub;
    private Memberships testPackage;
    private Registers testRegister;

    @BeforeEach
    void setUp() {
        // Mock Security Context
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("student@test.com");

        // Setup test data
        Roles studentRole = Roles.builder()
                .roleId(1)
                .roleName(RoleType.SinhVien)
                .build();

        testStudent = Users.builder()
                .userId("user-001")
                .email("student@test.com")
                .fullName("Nguyen Van A")
                .studentCode("SE160001")
                .role(studentRole)
                .enabled(true)
                .isActive(true)
                .build();

        Users founder = Users.builder()
                .userId("user-002")
                .email("founder@test.com")
                .fullName("Tran Van B")
                .build();

        testClub = Clubs.builder()
                .clubId(1)
                .clubName("CLB Lập Trình")
                .category(ClubCategory.HocThuat)
                .founder(founder)
                .isActive(true)
                .build();

        testPackage = Memberships.builder()
                .packageId(1)
                .packageName("Thành Viên Cơ Bản")
                .club(testClub)
                .price(new BigDecimal("50000"))
                .term("1 tháng")
                .isActive(true)
                .build();

        testRegister = Registers.builder()
                .subscriptionId(1)
                .user(testStudent)
                .membershipPackage(testPackage)
                .status(JoinStatus.ChoDuyet)
                .joinReason("Muốn học lập trình")
                .isPaid(false)
                .build();
    }

    @Test
    @DisplayName("TC01: Sinh viên đăng ký tham gia CLB thành công")
    void testJoinClub_Success() {
        // Given
        JoinClubRequest request = JoinClubRequest.builder()
                .packageId(1)
                .joinReason("Muốn học lập trình")
                .build();

        RegisterResponse expectedResponse = RegisterResponse.builder()
                .subscriptionId(1)
                .status(JoinStatus.ChoDuyet)
                .build();

        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(testStudent));
        when(membershipRepository.findById(1)).thenReturn(Optional.of(testPackage));
        when(registerRepository.findByUserAndMembershipPackage_Club_ClubId(testStudent, 1))
                .thenReturn(Optional.empty());
        when(registerRepository.save(any(Registers.class))).thenReturn(testRegister);
        when(registerMapper.toRegisterResponse(any(Registers.class))).thenReturn(expectedResponse);

        // When
        RegisterResponse result = registerService.joinClub(request);

        // Then
        assertNotNull(result);
        assertEquals(JoinStatus.ChoDuyet, result.getStatus());
        verify(registerRepository, times(1)).save(any(Registers.class));
        verify(registerMapper, times(1)).toRegisterResponse(any(Registers.class));
    }

    @Test
    @DisplayName("TC02: Không thể đăng ký CLB khi đang chờ duyệt")
    void testJoinClub_AlreadyPending() {
        // Given
        JoinClubRequest request = JoinClubRequest.builder()
                .packageId(1)
                .joinReason("Muốn học lập trình")
                .build();

        Registers pendingRegister = Registers.builder()
                .subscriptionId(1)
                .user(testStudent)
                .membershipPackage(testPackage)
                .status(JoinStatus.ChoDuyet)
                .isPaid(false)
                .build();

        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(testStudent));
        when(membershipRepository.findById(1)).thenReturn(Optional.of(testPackage));
        when(registerRepository.findByUserAndMembershipPackage_Club_ClubId(testStudent, 1))
                .thenReturn(Optional.of(pendingRegister));

        // When & Then
        AppException exception = assertThrows(AppException.class, () -> {
            registerService.joinClub(request);
        });

        assertEquals(ErrorCode.ALREADY_REGISTERED, exception.getErrorCode());
        verify(registerRepository, never()).save(any(Registers.class));
    }

    @Test
    @DisplayName("TC03: Không thể đăng ký CLB đã là thành viên")
    void testJoinClub_AlreadyMember() {
        // Given
        JoinClubRequest request = JoinClubRequest.builder()
                .packageId(1)
                .joinReason("Muốn học lập trình")
                .build();

        Registers activeRegister = Registers.builder()
                .subscriptionId(1)
                .user(testStudent)
                .membershipPackage(testPackage)
                .status(JoinStatus.DaDuyet)
                .isPaid(true)
                .build();

        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(testStudent));
        when(membershipRepository.findById(1)).thenReturn(Optional.of(testPackage));
        when(registerRepository.findByUserAndMembershipPackage_Club_ClubId(testStudent, 1))
                .thenReturn(Optional.of(activeRegister));

        // When & Then
        AppException exception = assertThrows(AppException.class, () -> {
            registerService.joinClub(request);
        });

        assertEquals(ErrorCode.ALREADY_MEMBER, exception.getErrorCode());
        verify(registerRepository, never()).save(any(Registers.class));
    }

    @Test
    @DisplayName("TC04: Có thể tái gia nhập sau khi bị từ chối")
    void testJoinClub_ReapplyAfterRejection() {
        // Given
        JoinClubRequest request = JoinClubRequest.builder()
                .packageId(1)
                .joinReason("Muốn học lập trình lần 2")
                .build();

        Registers rejectedRegister = Registers.builder()
                .subscriptionId(1)
                .user(testStudent)
                .membershipPackage(testPackage)
                .status(JoinStatus.TuChoi)
                .isPaid(false)
                .build();

        RegisterResponse expectedResponse = RegisterResponse.builder()
                .subscriptionId(1)
                .status(JoinStatus.ChoDuyet)
                .build();

        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(testStudent));
        when(membershipRepository.findById(1)).thenReturn(Optional.of(testPackage));
        when(registerRepository.findByUserAndMembershipPackage_Club_ClubId(testStudent, 1))
                .thenReturn(Optional.of(rejectedRegister));
        when(registerRepository.save(any(Registers.class))).thenReturn(rejectedRegister);
        when(registerMapper.toRegisterResponse(any(Registers.class))).thenReturn(expectedResponse);

        // When
        RegisterResponse result = registerService.joinClub(request);

        // Then
        assertNotNull(result);
        assertEquals(JoinStatus.ChoDuyet, result.getStatus());
        verify(registerRepository, times(1)).save(any(Registers.class));
    }

    @Test
    @DisplayName("TC05: Xem danh sách các CLB đã đăng ký")
    void testGetMyRegistrations_Success() {
        // Given
        List<Registers> mockRegistrations = new ArrayList<>();
        mockRegistrations.add(testRegister);

        List<RegisterResponse> expectedResponses = new ArrayList<>();
        expectedResponses.add(RegisterResponse.builder()
                .subscriptionId(1)
                .status(JoinStatus.ChoDuyet)
                .build());

        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(testStudent));
        when(registerRepository.findByUser(testStudent)).thenReturn(mockRegistrations);
        when(registerMapper.toRegisterResponseList(mockRegistrations)).thenReturn(expectedResponses);

        // When
        List<RegisterResponse> result = registerService.getMyRegistrations();

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(registerRepository, times(1)).findByUser(testStudent);
    }

    @Test
    @DisplayName("TC06: Package không tồn tại")
    void testJoinClub_PackageNotFound() {
        // Given
        JoinClubRequest request = JoinClubRequest.builder()
                .packageId(999)
                .joinReason("Muốn học lập trình")
                .build();

        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(testStudent));
        when(membershipRepository.findById(999)).thenReturn(Optional.empty());

        // When & Then
        AppException exception = assertThrows(AppException.class, () -> {
            registerService.joinClub(request);
        });

        assertEquals(ErrorCode.PACKAGE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("TC07: Package không active")
    void testJoinClub_PackageNotActive() {
        // Given
        JoinClubRequest request = JoinClubRequest.builder()
                .packageId(1)
                .joinReason("Muốn học lập trình")
                .build();

        testPackage.setIsActive(false);

        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(testStudent));
        when(membershipRepository.findById(1)).thenReturn(Optional.of(testPackage));

        // When & Then
        AppException exception = assertThrows(AppException.class, () -> {
            registerService.joinClub(request);
        });

        assertEquals(ErrorCode.PACKAGE_NOT_ACTIVE, exception.getErrorCode());
    }
}

