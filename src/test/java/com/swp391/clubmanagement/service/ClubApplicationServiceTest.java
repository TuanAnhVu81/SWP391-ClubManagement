package com.swp391.clubmanagement.service;

import com.swp391.clubmanagement.dto.request.ClubApplicationRequest;
import com.swp391.clubmanagement.dto.request.ReviewApplicationRequest;
import com.swp391.clubmanagement.dto.response.ClubApplicationResponse;
import com.swp391.clubmanagement.entity.*;
import com.swp391.clubmanagement.enums.ClubCategory;
import com.swp391.clubmanagement.enums.ClubRoleType;
import com.swp391.clubmanagement.enums.JoinStatus;
import com.swp391.clubmanagement.enums.RequestStatus;
import com.swp391.clubmanagement.enums.RoleType;
import com.swp391.clubmanagement.exception.AppException;
import com.swp391.clubmanagement.exception.ErrorCode;
import com.swp391.clubmanagement.mapper.ClubApplicationMapper;
import com.swp391.clubmanagement.repository.*;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho ClubApplicationService - Luồng tạo CLB
 * 
 * Test Cases:
 * 1. Sinh viên gửi đơn thành lập CLB thành công
 * 2. Sinh viên đang là thành viên CLB khác không được tạo CLB
 * 3. Admin duyệt đơn thành lập CLB thành công
 * 4. Admin từ chối đơn thành lập CLB
 * 5. Không thể duyệt đơn đã được xử lý
 * 6. Xem danh sách đơn theo trạng thái
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClubApplicationService Unit Tests - Luồng tạo CLB")
class ClubApplicationServiceTest {

    @Mock
    private ClubApplicationRepository clubApplicationRepository;

    @Mock
    private ClubRepository clubRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private RegisterRepository registerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private ClubApplicationMapper clubApplicationMapper;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ClubApplicationService clubApplicationService;

    private Users testStudent;
    private Users testAdmin;
    private ClubApplications testApplication;
    private Roles sinhVienRole;
    private Roles chuTichRole;
    private Roles adminRole;

    @BeforeEach
    void setUp() {
        // Setup roles
        sinhVienRole = Roles.builder()
                .roleId(1)
                .roleName(RoleType.SinhVien)
                .build();

        chuTichRole = Roles.builder()
                .roleId(2)
                .roleName(RoleType.ChuTich)
                .build();

        adminRole = Roles.builder()
                .roleId(3)
                .roleName(RoleType.QuanTriVien)
                .build();

        // Setup test student
        testStudent = Users.builder()
                .userId("user-001")
                .email("student@test.com")
                .fullName("Nguyen Van A")
                .studentCode("SE160001")
                .role(sinhVienRole)
                .enabled(true)
                .isActive(true)
                .build();

        // Setup test admin
        testAdmin = Users.builder()
                .userId("admin-001")
                .email("admin@test.com")
                .fullName("Admin Nguyen")
                .role(adminRole)
                .enabled(true)
                .isActive(true)
                .build();

        // Setup test application
        testApplication = ClubApplications.builder()
                .requestId(1)
                .creator(testStudent)
                .proposedName("CLB Lập Trình")
                .category(ClubCategory.HocThuat)
                .purpose("Học tập và chia sẻ kiến thức lập trình")
                .description("CLB cho sinh viên yêu thích lập trình")
                .location("Phòng A101")
                .email("laptrinhclub@test.com")
                .defaultMembershipFee(new BigDecimal("50000"))
                .status(RequestStatus.DangCho)
                .build();
    }

    @Test
    @DisplayName("TC01: Sinh viên gửi đơn thành lập CLB thành công")
    void testCreateClubApplication_Success() {
        // Given
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("student@test.com");

        ClubApplicationRequest request = ClubApplicationRequest.builder()
                .proposedName("CLB Lập Trình")
                .category(ClubCategory.HocThuat)
                .purpose("Học tập và chia sẻ kiến thức lập trình")
                .description("CLB cho sinh viên yêu thích lập trình")
                .location("Phòng A101")
                .email("laptrinhclub@test.com")
                .defaultMembershipFee(new BigDecimal("50000"))
                .build();

        ClubApplicationResponse expectedResponse = ClubApplicationResponse.builder()
                .requestId(1)
                .status(RequestStatus.DangCho)
                .build();

        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(testStudent));
        when(registerRepository.findByUserAndStatusAndIsPaid(testStudent, JoinStatus.DaDuyet, true))
                .thenReturn(List.of()); // Không là thành viên CLB nào
        when(clubApplicationRepository.save(any(ClubApplications.class))).thenReturn(testApplication);
        when(clubApplicationMapper.toResponse(any(ClubApplications.class))).thenReturn(expectedResponse);

        // When
        ClubApplicationResponse result = clubApplicationService.createClubApplication(request);

        // Then
        assertNotNull(result);
        assertEquals(RequestStatus.DangCho, result.getStatus());
        verify(clubApplicationRepository, times(1)).save(any(ClubApplications.class));
    }

    @Test
    @DisplayName("TC02: Sinh viên đang là thành viên CLB không được tạo CLB")
    void testCreateClubApplication_AlreadyMember() {
        // Given
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("student@test.com");

        ClubApplicationRequest request = ClubApplicationRequest.builder()
                .proposedName("CLB Lập Trình")
                .category(ClubCategory.HocThuat)
                .purpose("Học tập")
                .build();

        // Mock sinh viên đang là thành viên của 1 CLB khác
        Clubs existingClub = Clubs.builder()
                .clubId(1)
                .clubName("CLB Khác")
                .build();

        Memberships existingPackage = Memberships.builder()
                .packageId(1)
                .club(existingClub)
                .build();

        Registers activeRegister = Registers.builder()
                .subscriptionId(1)
                .user(testStudent)
                .membershipPackage(existingPackage)
                .status(JoinStatus.DaDuyet)
                .isPaid(true)
                .build();

        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(testStudent));
        when(registerRepository.findByUserAndStatusAndIsPaid(testStudent, JoinStatus.DaDuyet, true))
                .thenReturn(List.of(activeRegister)); // Đang là thành viên

        // When & Then
        AppException exception = assertThrows(AppException.class, () -> {
            clubApplicationService.createClubApplication(request);
        });

        assertEquals(ErrorCode.ALREADY_CLUB_MEMBER, exception.getErrorCode());
        verify(clubApplicationRepository, never()).save(any(ClubApplications.class));
    }

    @Test
    @DisplayName("TC03: Admin duyệt đơn thành lập CLB thành công")
    void testReviewApplication_Approve_Success() {
        // Given
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("admin@test.com");

        ReviewApplicationRequest request = ReviewApplicationRequest.builder()
                .status(RequestStatus.ChapThuan)
                .adminNote("Đơn hợp lệ, chấp thuận")
                .build();

        Clubs newClub = Clubs.builder()
                .clubId(1)
                .clubName("CLB Lập Trình")
                .founder(testStudent)
                .isActive(true)
                .build();

        Memberships defaultPackage = Memberships.builder()
                .packageId(1)
                .club(newClub)
                .packageName("Thành Viên Cơ Bản")
                .build();

        Registers founderRegister = Registers.builder()
                .subscriptionId(1)
                .user(testStudent)
                .membershipPackage(defaultPackage)
                .status(JoinStatus.DaDuyet)
                .clubRole(ClubRoleType.ChuTich)
                .isPaid(true)
                .build();

        ClubApplicationResponse expectedResponse = ClubApplicationResponse.builder()
                .requestId(1)
                .status(RequestStatus.ChapThuan)
                .build();

        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(testAdmin));
        when(clubApplicationRepository.findById(1)).thenReturn(Optional.of(testApplication));
        when(clubRepository.save(any(Clubs.class))).thenReturn(newClub);
        when(membershipRepository.save(any(Memberships.class))).thenReturn(defaultPackage);
        when(registerRepository.save(any(Registers.class))).thenReturn(founderRegister);
        when(roleRepository.findByRoleName(RoleType.ChuTich)).thenReturn(Optional.of(chuTichRole));
        when(userRepository.findById(testStudent.getUserId())).thenReturn(Optional.of(testStudent));
        when(userRepository.save(any(Users.class))).thenReturn(testStudent);
        when(clubApplicationRepository.save(any(ClubApplications.class))).thenReturn(testApplication);
        when(clubApplicationMapper.toResponse(any(ClubApplications.class))).thenReturn(expectedResponse);

        // When
        ClubApplicationResponse result = clubApplicationService.reviewApplication(1, request);

        // Then
        assertNotNull(result);
        assertEquals(RequestStatus.ChapThuan, result.getStatus());
        verify(clubRepository, times(1)).save(any(Clubs.class));
        verify(membershipRepository, times(1)).save(any(Memberships.class));
        verify(registerRepository, atLeastOnce()).save(any(Registers.class));
        verify(userRepository, times(1)).save(any(Users.class));
    }

    @Test
    @DisplayName("TC04: Admin từ chối đơn thành lập CLB")
    void testReviewApplication_Reject_Success() {
        // Given
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("admin@test.com");

        ReviewApplicationRequest request = ReviewApplicationRequest.builder()
                .status(RequestStatus.TuChoi)
                .adminNote("Đơn không đủ điều kiện")
                .build();

        ClubApplicationResponse expectedResponse = ClubApplicationResponse.builder()
                .requestId(1)
                .status(RequestStatus.TuChoi)
                .build();

        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(testAdmin));
        when(clubApplicationRepository.findById(1)).thenReturn(Optional.of(testApplication));
        when(clubApplicationRepository.save(any(ClubApplications.class))).thenReturn(testApplication);
        when(clubApplicationMapper.toResponse(any(ClubApplications.class))).thenReturn(expectedResponse);

        // When
        ClubApplicationResponse result = clubApplicationService.reviewApplication(1, request);

        // Then
        assertNotNull(result);
        assertEquals(RequestStatus.TuChoi, result.getStatus());
        verify(clubRepository, never()).save(any(Clubs.class)); // Không tạo CLB
        verify(membershipRepository, never()).save(any(Memberships.class));
        verify(registerRepository, never()).save(any(Registers.class));
    }

    @Test
    @DisplayName("TC05: Không thể duyệt đơn đã được xử lý")
    void testReviewApplication_AlreadyReviewed() {
        // Given
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("admin@test.com");

        testApplication.setStatus(RequestStatus.ChapThuan); // Đã duyệt rồi

        ReviewApplicationRequest request = ReviewApplicationRequest.builder()
                .status(RequestStatus.ChapThuan)
                .adminNote("Test")
                .build();

        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(testAdmin));
        when(clubApplicationRepository.findById(1)).thenReturn(Optional.of(testApplication));

        // When & Then
        AppException exception = assertThrows(AppException.class, () -> {
            clubApplicationService.reviewApplication(1, request);
        });

        assertEquals(ErrorCode.APPLICATION_ALREADY_REVIEWED, exception.getErrorCode());
        verify(clubApplicationRepository, never()).save(any(ClubApplications.class));
    }

    @Test
    @DisplayName("TC06: Xem danh sách đơn theo trạng thái")
    void testGetAllApplications_ByStatus() {
        // Given
        SecurityContextHolder.setContext(securityContext);

        List<ClubApplications> mockApplications = List.of(testApplication);
        List<ClubApplicationResponse> expectedResponses = List.of(
                ClubApplicationResponse.builder()
                        .requestId(1)
                        .status(RequestStatus.DangCho)
                        .build()
        );

        when(clubApplicationRepository.findByStatus(RequestStatus.DangCho)).thenReturn(mockApplications);
        when(clubApplicationMapper.toResponse(any(ClubApplications.class)))
                .thenReturn(expectedResponses.get(0));

        // When
        List<ClubApplicationResponse> result = clubApplicationService.getAllApplications(RequestStatus.DangCho);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(RequestStatus.DangCho, result.get(0).getStatus());
        verify(clubApplicationRepository, times(1)).findByStatus(RequestStatus.DangCho);
    }

    @Test
    @DisplayName("TC07: Đơn không tồn tại")
    void testReviewApplication_NotFound() {
        // Given
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("admin@test.com");

        ReviewApplicationRequest request = ReviewApplicationRequest.builder()
                .status(RequestStatus.ChapThuan)
                .build();

        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(testAdmin));
        when(clubApplicationRepository.findById(999)).thenReturn(Optional.empty());

        // When & Then
        AppException exception = assertThrows(AppException.class, () -> {
            clubApplicationService.reviewApplication(999, request);
        });

        assertEquals(ErrorCode.APPLICATION_NOT_FOUND, exception.getErrorCode());
    }
}

