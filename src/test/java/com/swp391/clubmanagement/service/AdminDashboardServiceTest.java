package com.swp391.clubmanagement.service;

import com.swp391.clubmanagement.dto.response.AdminDashboardResponse;
import com.swp391.clubmanagement.dto.response.ClubResponse;
import com.swp391.clubmanagement.dto.response.ClubStatistic;
import com.swp391.clubmanagement.entity.Clubs;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.enums.ClubCategory;
import com.swp391.clubmanagement.enums.ClubRoleType;
import com.swp391.clubmanagement.enums.JoinStatus;
import com.swp391.clubmanagement.mapper.ClubMapper;
import com.swp391.clubmanagement.repository.ClubRepository;
import com.swp391.clubmanagement.repository.RegisterRepository;
import com.swp391.clubmanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho AdminDashboardService - Thống kê và báo cáo
 * 
 * Test Cases:
 * 1. Lấy tổng số CLB đang hoạt động
 * 2. Lấy tổng số thành viên (registrations)
 * 3. Lấy tổng số sinh viên duy nhất
 * 4. Thống kê CLB theo category
 * 5. Thống kê thành viên theo vai trò
 * 6. Top 5 CLB có nhiều thành viên nhất
 * 7. Danh sách CLB mới trong tháng
 * 8. Lấy dữ liệu tổng quan Dashboard
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminDashboardService Unit Tests - Thống kê và báo cáo")
class AdminDashboardServiceTest {

    @Mock
    private ClubRepository clubRepository;

    @Mock
    private RegisterRepository registerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClubMapper clubMapper;

    @InjectMocks
    private AdminDashboardService adminDashboardService;

    private Clubs testClub1;
    private Clubs testClub2;
    private Users testFounder;

    @BeforeEach
    void setUp() {
        testFounder = Users.builder()
                .userId("user-001")
                .email("founder@test.com")
                .fullName("Founder Test")
                .build();

        testClub1 = Clubs.builder()
                .clubId(1)
                .clubName("CLB Lập Trình")
                .category(ClubCategory.HocThuat)
                .founder(testFounder)
                .isActive(true)
                .establishedDate(LocalDate.now())
                .build();

        testClub2 = Clubs.builder()
                .clubId(2)
                .clubName("CLB Bóng Đá")
                .category(ClubCategory.TheThao)
                .founder(testFounder)
                .isActive(true)
                .establishedDate(LocalDate.now().minusMonths(2))
                .build();
    }

    @Test
    @DisplayName("TC01: Lấy tổng số CLB đang hoạt động")
    void testGetTotalClubs_Success() {
        // Given
        when(clubRepository.countByIsActiveTrue()).thenReturn(10L);

        // When
        Long result = adminDashboardService.getTotalClubs();

        // Then
        assertNotNull(result);
        assertEquals(10L, result);
        verify(clubRepository, times(1)).countByIsActiveTrue();
    }

    @Test
    @DisplayName("TC02: Lấy tổng số thành viên (registrations)")
    void testGetTotalMembers_Success() {
        // Given
        when(registerRepository.countByStatusAndIsPaid(JoinStatus.DaDuyet, true)).thenReturn(150L);

        // When
        Long result = adminDashboardService.getTotalMembers();

        // Then
        assertNotNull(result);
        assertEquals(150L, result);
        verify(registerRepository, times(1)).countByStatusAndIsPaid(JoinStatus.DaDuyet, true);
    }

    @Test
    @DisplayName("TC03: Lấy tổng số sinh viên duy nhất")
    void testGetTotalStudents_Success() {
        // Given
        when(registerRepository.countDistinctStudents(JoinStatus.DaDuyet, true)).thenReturn(100L);

        // When
        Long result = adminDashboardService.getTotalStudents();

        // Then
        assertNotNull(result);
        assertEquals(100L, result);
        verify(registerRepository, times(1)).countDistinctStudents(JoinStatus.DaDuyet, true);
    }

    @Test
    @DisplayName("TC04: Thống kê CLB theo category")
    void testGetClubsByCategory_Success() {
        // Given
        List<Object[]> mockData = new ArrayList<>();
        mockData.add(new Object[]{ClubCategory.HocThuat, 5L});
        mockData.add(new Object[]{ClubCategory.TheThao, 3L});
        mockData.add(new Object[]{ClubCategory.NgheThuat, 2L});

        when(clubRepository.countByCategory()).thenReturn(mockData);

        // When
        Map<String, Long> result = adminDashboardService.getClubsByCategory();

        // Then
        assertNotNull(result);
        assertEquals(5L, result.get("HocThuat"));
        assertEquals(3L, result.get("TheThao"));
        assertEquals(2L, result.get("NgheThuat"));
        
        // Kiểm tra tất cả categories đều có trong map (kể cả 0)
        for (ClubCategory category : ClubCategory.values()) {
            assertTrue(result.containsKey(category.name()));
        }
        
        verify(clubRepository, times(1)).countByCategory();
    }

    @Test
    @DisplayName("TC05: Thống kê thành viên theo vai trò")
    void testGetMembersByRole_Success() {
        // Given
        List<Object[]> mockData = new ArrayList<>();
        mockData.add(new Object[]{ClubRoleType.ChuTich, 10L});
        mockData.add(new Object[]{ClubRoleType.PhoChuTich, 15L});
        mockData.add(new Object[]{ClubRoleType.ThuKy, 20L});
        mockData.add(new Object[]{ClubRoleType.ThanhVien, 100L});

        when(registerRepository.countByClubRole(JoinStatus.DaDuyet, true)).thenReturn(mockData);

        // When
        Map<String, Long> result = adminDashboardService.getMembersByRole();

        // Then
        assertNotNull(result);
        assertEquals(10L, result.get("ChuTich"));
        assertEquals(15L, result.get("PhoChuTich"));
        assertEquals(20L, result.get("ThuKy"));
        assertEquals(100L, result.get("ThanhVien"));
        
        // Kiểm tra tất cả roles đều có trong map
        for (ClubRoleType role : ClubRoleType.values()) {
            assertTrue(result.containsKey(role.name()));
        }
        
        verify(registerRepository, times(1)).countByClubRole(JoinStatus.DaDuyet, true);
    }

    @Test
    @DisplayName("TC06: Top 5 CLB có nhiều thành viên nhất")
    void testGetTop5ClubsByMembers_Success() {
        // Given
        List<Object[]> mockData = new ArrayList<>();
        mockData.add(new Object[]{1, "CLB Lập Trình", "logo1.jpg", ClubCategory.HocThuat, 50L});
        mockData.add(new Object[]{2, "CLB Bóng Đá", "logo2.jpg", ClubCategory.TheThao, 45L});
        mockData.add(new Object[]{3, "CLB Âm Nhạc", "logo3.jpg", ClubCategory.NgheThuat, 40L});
        mockData.add(new Object[]{4, "CLB Tiếng Anh", "logo4.jpg", ClubCategory.HocThuat, 35L});
        mockData.add(new Object[]{5, "CLB Nhiếp Ảnh", "logo5.jpg", ClubCategory.NgheThuat, 30L});
        mockData.add(new Object[]{6, "CLB Yoga", "logo6.jpg", ClubCategory.TheThao, 25L}); // Không nằm trong top 5

        when(registerRepository.findTopClubsByMemberCount(JoinStatus.DaDuyet, true)).thenReturn(mockData);

        // When
        List<ClubStatistic> result = adminDashboardService.getTop5ClubsByMembers();

        // Then
        assertNotNull(result);
        assertEquals(5, result.size()); // Chỉ lấy top 5
        assertEquals("CLB Lập Trình", result.get(0).getClubName());
        assertEquals(50L, result.get(0).getMemberCount());
        assertEquals("CLB Nhiếp Ảnh", result.get(4).getClubName());
        assertEquals(30L, result.get(4).getMemberCount());
        
        verify(registerRepository, times(1)).findTopClubsByMemberCount(JoinStatus.DaDuyet, true);
    }

    @Test
    @DisplayName("TC07: Danh sách CLB mới trong tháng")
    void testGetNewClubsThisMonth_Success() {
        // Given
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        List<Clubs> newClubs = List.of(testClub1);
        
        ClubResponse clubResponse1 = ClubResponse.builder()
                .clubId(1)
                .clubName("CLB Lập Trình")
                .category(ClubCategory.HocThuat)
                .build();

        when(clubRepository.findNewClubsThisMonth(startOfMonth)).thenReturn(newClubs);
        when(clubMapper.toResponse(testClub1)).thenReturn(clubResponse1);

        // When
        List<ClubResponse> result = adminDashboardService.getNewClubsThisMonth();

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("CLB Lập Trình", result.get(0).getClubName());
        verify(clubRepository, times(1)).findNewClubsThisMonth(any(LocalDate.class));
    }

    @Test
    @DisplayName("TC08: Lấy dữ liệu tổng quan Dashboard")
    void testGetDashboardData_Success() {
        // Given
        when(clubRepository.countByIsActiveTrue()).thenReturn(10L);
        when(registerRepository.countByStatusAndIsPaid(JoinStatus.DaDuyet, true)).thenReturn(150L);
        when(registerRepository.countDistinctStudents(JoinStatus.DaDuyet, true)).thenReturn(100L);
        
        List<Object[]> categoryData = new ArrayList<>();
        categoryData.add(new Object[]{ClubCategory.HocThuat, 5L});
        when(clubRepository.countByCategory()).thenReturn(categoryData);
        
        List<Object[]> roleData = new ArrayList<>();
        roleData.add(new Object[]{ClubRoleType.ThanhVien, 100L});
        when(registerRepository.countByClubRole(JoinStatus.DaDuyet, true)).thenReturn(roleData);
        
        List<Object[]> topClubsData = new ArrayList<>();
        topClubsData.add(new Object[]{1, "CLB Lập Trình", "logo1.jpg", ClubCategory.HocThuat, 50L});
        when(registerRepository.findTopClubsByMemberCount(JoinStatus.DaDuyet, true)).thenReturn(topClubsData);
        
        List<Clubs> newClubs = List.of(testClub1);
        when(clubRepository.findNewClubsThisMonth(any(LocalDate.class))).thenReturn(newClubs);
        when(clubMapper.toResponse(any(Clubs.class))).thenReturn(ClubResponse.builder().build());

        // When
        AdminDashboardResponse result = adminDashboardService.getDashboardData();

        // Then
        assertNotNull(result);
        assertEquals(10L, result.getTotalClubs());
        assertEquals(150L, result.getTotalMembers());
        assertEquals(100L, result.getTotalStudents());
        assertNotNull(result.getClubsByCategory());
        assertNotNull(result.getMembersByRole());
        assertNotNull(result.getTop5ClubsByMembers());
        assertNotNull(result.getNewClubsThisMonth());
        
        // Verify tất cả các service methods được gọi
        verify(clubRepository, times(1)).countByIsActiveTrue();
        verify(registerRepository, times(1)).countByStatusAndIsPaid(JoinStatus.DaDuyet, true);
        verify(registerRepository, times(1)).countDistinctStudents(JoinStatus.DaDuyet, true);
        verify(clubRepository, times(1)).countByCategory();
        verify(registerRepository, times(1)).countByClubRole(JoinStatus.DaDuyet, true);
        verify(registerRepository, times(1)).findTopClubsByMemberCount(JoinStatus.DaDuyet, true);
        verify(clubRepository, times(1)).findNewClubsThisMonth(any(LocalDate.class));
    }

    @Test
    @DisplayName("TC09: Trường hợp không có CLB nào")
    void testGetDashboardData_NoClubs() {
        // Given
        when(clubRepository.countByIsActiveTrue()).thenReturn(0L);
        when(registerRepository.countByStatusAndIsPaid(JoinStatus.DaDuyet, true)).thenReturn(0L);
        when(registerRepository.countDistinctStudents(JoinStatus.DaDuyet, true)).thenReturn(0L);
        when(clubRepository.countByCategory()).thenReturn(new ArrayList<>());
        when(registerRepository.countByClubRole(JoinStatus.DaDuyet, true)).thenReturn(new ArrayList<>());
        when(registerRepository.findTopClubsByMemberCount(JoinStatus.DaDuyet, true)).thenReturn(new ArrayList<>());
        when(clubRepository.findNewClubsThisMonth(any(LocalDate.class))).thenReturn(new ArrayList<>());

        // When
        AdminDashboardResponse result = adminDashboardService.getDashboardData();

        // Then
        assertNotNull(result);
        assertEquals(0L, result.getTotalClubs());
        assertEquals(0L, result.getTotalMembers());
        assertEquals(0L, result.getTotalStudents());
        assertTrue(result.getTop5ClubsByMembers().isEmpty());
        assertTrue(result.getNewClubsThisMonth().isEmpty());
    }
}

