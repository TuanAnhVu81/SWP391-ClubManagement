package com.swp391.clubmanagement.service;

import com.swp391.clubmanagement.dto.request.ApproveRegisterRequest;
import com.swp391.clubmanagement.dto.request.ConfirmPaymentRequest;
import com.swp391.clubmanagement.dto.response.RegisterResponse;
import com.swp391.clubmanagement.entity.Clubs;
import com.swp391.clubmanagement.entity.Memberships;
import com.swp391.clubmanagement.entity.Registers;
import com.swp391.clubmanagement.entity.Roles;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.enums.ClubCategory;
import com.swp391.clubmanagement.enums.ClubRoleType;
import com.swp391.clubmanagement.enums.JoinStatus;
import com.swp391.clubmanagement.enums.RoleType;
import com.swp391.clubmanagement.exception.AppException;
import com.swp391.clubmanagement.exception.ErrorCode;
import com.swp391.clubmanagement.mapper.RegisterMapper;
import com.swp391.clubmanagement.repository.RegisterRepository;
import com.swp391.clubmanagement.repository.RoleRepository;
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
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho LeaderRegisterService - Luồng Chủ tịch duyệt đơn và xác nhận thanh toán
 * 
 * Test Cases:
 * 1. Chủ tịch duyệt đơn thành công (DaDuyet)
 * 2. Chủ tịch từ chối đơn (TuChoi)
 * 3. Chủ tịch xác nhận thanh toán thành công
 * 4. Không thể duyệt đơn đã được xử lý
 * 5. Không thể xác nhận thanh toán khi chưa duyệt
 * 6. Xem danh sách đơn đăng ký theo trạng thái
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LeaderRegisterService Unit Tests - Luồng duyệt đơn và thanh toán")
class LeaderRegisterServiceTest {

    @Mock
    private RegisterRepository registerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RegisterMapper registerMapper;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private LeaderRegisterService leaderRegisterService;

    private Users testPresident;
    private Users testStudent;
    private Clubs testClub;
    private Memberships testPackage;
    private Registers testRegister;

    @BeforeEach
    void setUp() {
        // Mock Security Context
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("president@test.com");

        // Setup test data
        Roles presidentRole = Roles.builder()
                .roleId(2)
                .roleName(RoleType.ChuTich)
                .build();

        Roles studentRole = Roles.builder()
                .roleId(1)
                .roleName(RoleType.SinhVien)
                .build();

        testPresident = Users.builder()
                .userId("user-001")
                .email("president@test.com")
                .fullName("Tran Van B")
                .studentCode("SE160002")
                .role(presidentRole)
                .enabled(true)
                .isActive(true)
                .build();

        testStudent = Users.builder()
                .userId("user-002")
                .email("student@test.com")
                .fullName("Nguyen Van A")
                .studentCode("SE160001")
                .role(studentRole)
                .enabled(true)
                .isActive(true)
                .build();

        testClub = Clubs.builder()
                .clubId(1)
                .clubName("CLB Lập Trình")
                .category(ClubCategory.HocThuat)
                .founder(testPresident)
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
    @DisplayName("TC01: Chủ tịch duyệt đơn thành công")
    void testApproveRegistration_Success() {
        // Given
        ApproveRegisterRequest request = ApproveRegisterRequest.builder()
                .subscriptionId(1)
                .status(JoinStatus.DaDuyet)
                .build();

        RegisterResponse expectedResponse = RegisterResponse.builder()
                .subscriptionId(1)
                .status(JoinStatus.DaDuyet)
                .build();

        when(userRepository.findByEmail("president@test.com")).thenReturn(Optional.of(testPresident));
        when(registerRepository.findById(1)).thenReturn(Optional.of(testRegister));
        when(registerRepository.existsByUserAndMembershipPackage_Club_ClubIdAndClubRoleInAndStatusAndIsPaid(
                eq(testPresident), eq(1), any(List.class), eq(JoinStatus.DaDuyet), eq(true)))
                .thenReturn(true);
        when(registerRepository.save(any(Registers.class))).thenReturn(testRegister);
        when(registerMapper.toRegisterResponse(any(Registers.class))).thenReturn(expectedResponse);

        // When
        RegisterResponse result = leaderRegisterService.approveRegistration(request);

        // Then
        assertNotNull(result);
        assertEquals(JoinStatus.DaDuyet, result.getStatus());
        verify(registerRepository, times(1)).save(any(Registers.class));
    }

    @Test
    @DisplayName("TC02: Chủ tịch từ chối đơn")
    void testRejectRegistration_Success() {
        // Given
        ApproveRegisterRequest request = ApproveRegisterRequest.builder()
                .subscriptionId(1)
                .status(JoinStatus.TuChoi)
                .build();

        RegisterResponse expectedResponse = RegisterResponse.builder()
                .subscriptionId(1)
                .status(JoinStatus.TuChoi)
                .build();

        when(userRepository.findByEmail("president@test.com")).thenReturn(Optional.of(testPresident));
        when(registerRepository.findById(1)).thenReturn(Optional.of(testRegister));
        when(registerRepository.existsByUserAndMembershipPackage_Club_ClubIdAndClubRoleInAndStatusAndIsPaid(
                eq(testPresident), eq(1), any(List.class), eq(JoinStatus.DaDuyet), eq(true)))
                .thenReturn(true);
        when(registerRepository.save(any(Registers.class))).thenReturn(testRegister);
        when(registerMapper.toRegisterResponse(any(Registers.class))).thenReturn(expectedResponse);

        // When
        RegisterResponse result = leaderRegisterService.approveRegistration(request);

        // Then
        assertNotNull(result);
        assertEquals(JoinStatus.TuChoi, result.getStatus());
        verify(registerRepository, times(1)).save(any(Registers.class));
    }

    @Test
    @DisplayName("TC03: Xác nhận thanh toán thành công")
    void testConfirmPayment_Success() {
        // Given
        testRegister.setStatus(JoinStatus.DaDuyet);
        testRegister.setJoinDate(LocalDateTime.now());

        ConfirmPaymentRequest request = ConfirmPaymentRequest.builder()
                .subscriptionId(1)
                .paymentMethod("TIỀN MẶT")
                .build();

        RegisterResponse expectedResponse = RegisterResponse.builder()
                .subscriptionId(1)
                .status(JoinStatus.DaDuyet)
                .isPaid(true)
                .build();

        when(userRepository.findByEmail("president@test.com")).thenReturn(Optional.of(testPresident));
        when(registerRepository.findById(1)).thenReturn(Optional.of(testRegister));
        when(registerRepository.existsByUserAndMembershipPackage_Club_ClubIdAndClubRoleInAndStatusAndIsPaid(
                eq(testPresident), eq(1), any(List.class), eq(JoinStatus.DaDuyet), eq(true)))
                .thenReturn(true);
        when(registerRepository.save(any(Registers.class))).thenReturn(testRegister);
        when(registerMapper.toRegisterResponse(any(Registers.class))).thenReturn(expectedResponse);

        // When
        RegisterResponse result = leaderRegisterService.confirmPayment(request);

        // Then
        assertNotNull(result);
        assertTrue(result.getIsPaid());
        verify(registerRepository, times(1)).save(any(Registers.class));
    }

    @Test
    @DisplayName("TC04: Không thể duyệt đơn đã được xử lý")
    void testApproveRegistration_AlreadyReviewed() {
        // Given
        testRegister.setStatus(JoinStatus.DaDuyet); // Đã duyệt rồi

        ApproveRegisterRequest request = ApproveRegisterRequest.builder()
                .subscriptionId(1)
                .status(JoinStatus.DaDuyet)
                .build();

        when(userRepository.findByEmail("president@test.com")).thenReturn(Optional.of(testPresident));
        when(registerRepository.findById(1)).thenReturn(Optional.of(testRegister));
        when(registerRepository.existsByUserAndMembershipPackage_Club_ClubIdAndClubRoleInAndStatusAndIsPaid(
                eq(testPresident), eq(1), any(List.class), eq(JoinStatus.DaDuyet), eq(true)))
                .thenReturn(true);

        // When & Then
        AppException exception = assertThrows(AppException.class, () -> {
            leaderRegisterService.approveRegistration(request);
        });

        assertEquals(ErrorCode.APPLICATION_ALREADY_REVIEWED, exception.getErrorCode());
        verify(registerRepository, never()).save(any(Registers.class));
    }

    @Test
    @DisplayName("TC05: Không thể xác nhận thanh toán khi chưa duyệt")
    void testConfirmPayment_NotApproved() {
        // Given
        testRegister.setStatus(JoinStatus.ChoDuyet); // Chưa duyệt

        ConfirmPaymentRequest request = ConfirmPaymentRequest.builder()
                .subscriptionId(1)
                .paymentMethod("TIỀN MẶT")
                .build();

        when(userRepository.findByEmail("president@test.com")).thenReturn(Optional.of(testPresident));
        when(registerRepository.findById(1)).thenReturn(Optional.of(testRegister));
        when(registerRepository.existsByUserAndMembershipPackage_Club_ClubIdAndClubRoleInAndStatusAndIsPaid(
                eq(testPresident), eq(1), any(List.class), eq(JoinStatus.DaDuyet), eq(true)))
                .thenReturn(true);

        // When & Then
        AppException exception = assertThrows(AppException.class, () -> {
            leaderRegisterService.confirmPayment(request);
        });

        assertEquals(ErrorCode.INVALID_APPLICATION_STATUS, exception.getErrorCode());
        verify(registerRepository, never()).save(any(Registers.class));
    }

    @Test
    @DisplayName("TC06: User không phải Leader")
    void testApproveRegistration_NotLeader() {
        // Given
        ApproveRegisterRequest request = ApproveRegisterRequest.builder()
                .subscriptionId(1)
                .status(JoinStatus.DaDuyet)
                .build();

        when(userRepository.findByEmail("president@test.com")).thenReturn(Optional.of(testPresident));
        when(registerRepository.findById(1)).thenReturn(Optional.of(testRegister));
        when(registerRepository.existsByUserAndMembershipPackage_Club_ClubIdAndClubRoleInAndStatusAndIsPaid(
                eq(testPresident), eq(1), any(List.class), eq(JoinStatus.DaDuyet), eq(true)))
                .thenReturn(false); // Không phải leader

        // When & Then
        AppException exception = assertThrows(AppException.class, () -> {
            leaderRegisterService.approveRegistration(request);
        });

        assertEquals(ErrorCode.NOT_CLUB_LEADER, exception.getErrorCode());
        verify(registerRepository, never()).save(any(Registers.class));
    }

    @Test
    @DisplayName("TC07: Xem danh sách đơn theo trạng thái")
    void testGetClubRegistrationsByStatus_Success() {
        // Given
        List<Registers> mockRegistrations = Arrays.asList(testRegister);
        List<RegisterResponse> expectedResponses = Arrays.asList(
                RegisterResponse.builder()
                        .subscriptionId(1)
                        .status(JoinStatus.ChoDuyet)
                        .build()
        );

        when(userRepository.findByEmail("president@test.com")).thenReturn(Optional.of(testPresident));
        when(registerRepository.existsByUserAndMembershipPackage_Club_ClubIdAndClubRoleInAndStatusAndIsPaid(
                eq(testPresident), eq(1), any(List.class), eq(JoinStatus.DaDuyet), eq(true)))
                .thenReturn(true);
        when(registerRepository.findByMembershipPackage_Club_ClubIdAndStatus(1, JoinStatus.ChoDuyet))
                .thenReturn(mockRegistrations);
        when(registerMapper.toRegisterResponseList(mockRegistrations)).thenReturn(expectedResponses);

        // When
        List<RegisterResponse> result = leaderRegisterService.getClubRegistrationsByStatus(1, JoinStatus.ChoDuyet);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(registerRepository, times(1))
                .findByMembershipPackage_Club_ClubIdAndStatus(1, JoinStatus.ChoDuyet);
    }
}

