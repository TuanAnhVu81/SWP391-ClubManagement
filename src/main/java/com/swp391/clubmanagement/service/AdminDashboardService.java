// Package định nghĩa service layer - xử lý business logic cho Admin Dashboard
package com.swp391.clubmanagement.service;

// ========== DTO ==========
import com.swp391.clubmanagement.dto.response.AdminDashboardResponse; // Response dữ liệu dashboard
import com.swp391.clubmanagement.dto.response.ClubResponse; // Response thông tin CLB
import com.swp391.clubmanagement.dto.response.ClubStatistic; // Response thống kê CLB

// ========== Entity ==========
import com.swp391.clubmanagement.entity.Clubs; // Entity CLB

// ========== Enum ==========
import com.swp391.clubmanagement.enums.ClubCategory; // Danh mục CLB
import com.swp391.clubmanagement.enums.ClubRoleType; // Vai trò trong CLB
import com.swp391.clubmanagement.enums.JoinStatus; // Trạng thái tham gia

// ========== Mapper ==========
import com.swp391.clubmanagement.mapper.ClubMapper; // Chuyển đổi Entity <-> DTO

// ========== Repository ==========
import com.swp391.clubmanagement.repository.ClubRepository; // Repository cho bảng Clubs
import com.swp391.clubmanagement.repository.RegisterRepository; // Repository cho bảng Registers
import com.swp391.clubmanagement.repository.UserRepository; // Repository cho bảng Users

// ========== Lombok ==========
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor; // Tự động tạo constructor inject dependencies
import lombok.experimental.FieldDefaults; // Tự động thêm private final cho fields
import lombok.extern.slf4j.Slf4j; // Tự động tạo logger

// ========== Spring Framework ==========
import org.springframework.stereotype.Service; // Đánh dấu class là Spring Service Bean

// ========== Java Standard Library ==========
import java.time.LocalDate; // Ngày
import java.util.HashMap; // Map
import java.util.List; // Danh sách
import java.util.Map; // Map interface
import java.util.stream.Collectors; // Collect stream thành collection

/**
 * Service quản lý Admin Dashboard
 * 
 * Chức năng chính:
 * - Lấy dữ liệu tổng quan cho Dashboard Admin
 * - Thống kê số lượng CLB, thành viên, sinh viên
 * - Thống kê CLB theo danh mục (category)
 * - Thống kê thành viên theo vai trò (ClubRoleType)
 * - Top 5 CLB có nhiều thành viên nhất
 * - Danh sách CLB mới trong tháng
 * 
 * Business Rules:
 * - Chỉ Admin mới được xem dashboard (được kiểm tra ở Controller)
 * - Tổng số thành viên = số lượng registration (1 sinh viên có thể tham gia nhiều CLB)
 * - Tổng số sinh viên = số lượng user duy nhất đã tham gia CLB
 * 
 * @Service: Spring Service Bean, được quản lý bởi IoC Container
 * @RequiredArgsConstructor: Lombok tự động tạo constructor inject dependencies
 * @FieldDefaults: Tự động thêm private final cho các field
 * @Slf4j: Tự động tạo logger với tên "log"
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AdminDashboardService {
    /** Repository thao tác với bảng clubs */
    ClubRepository clubRepository;
    
    /** Repository thao tác với bảng registers */
    RegisterRepository registerRepository;
    
    /** Repository thao tác với bảng users */
    UserRepository userRepository;
    
    /** Mapper chuyển đổi Entity (Clubs) <-> DTO (ClubResponse) */
    ClubMapper clubMapper;

    /**
     * Lấy dữ liệu tổng quan cho Dashboard Admin
     */
    public AdminDashboardResponse getDashboardData() {
        return AdminDashboardResponse.builder()
                .totalClubs(getTotalClubs())
                .totalMembers(getTotalMembers())
                .totalStudents(getTotalStudents())
                .clubsByCategory(getClubsByCategory())
                .membersByRole(getMembersByRole())
                .top5ClubsByMembers(getTop5ClubsByMembers())
                .newClubsThisMonth(getNewClubsThisMonth())
                .build();
    }

    /**
     * Tổng số CLB đang hoạt động
     */
    public Long getTotalClubs() {
        return clubRepository.countByIsActiveTrue();
    }

    /**
     * Tổng số thành viên (đã duyệt + đã thanh toán)
     * 1 sinh viên tham gia nhiều CLB = nhiều membership
     */
    public Long getTotalMembers() {
        return registerRepository.countByStatusAndIsPaid(JoinStatus.DaDuyet, true);
    }

    /**
     * Tổng số sinh viên duy nhất tham gia CLB
     */
    public Long getTotalStudents() {
        return registerRepository.countDistinctStudents(JoinStatus.DaDuyet, true);
    }

    /**
     * Thống kê CLB theo danh mục (category)
     */
    public Map<String, Long> getClubsByCategory() {
        Map<String, Long> result = new HashMap<>();
        
        // Khởi tạo tất cả category với giá trị 0
        for (ClubCategory category : ClubCategory.values()) {
            result.put(category.name(), 0L);
        }
        
        // Cập nhật với dữ liệu thực
        List<Object[]> data = clubRepository.countByCategory();
        for (Object[] row : data) {
            ClubCategory category = (ClubCategory) row[0];
            Long count = (Long) row[1];
            if (category != null) {
                result.put(category.name(), count);
            }
        }
        
        return result;
    }

    /**
     * Thống kê thành viên theo vai trò (ClubRoleType)
     */
    public Map<String, Long> getMembersByRole() {
        Map<String, Long> result = new HashMap<>();
        
        // Khởi tạo tất cả role với giá trị 0
        for (ClubRoleType role : ClubRoleType.values()) {
            result.put(role.name(), 0L);
        }
        
        // Cập nhật với dữ liệu thực
        List<Object[]> data = registerRepository.countByClubRole(JoinStatus.DaDuyet, true);
        for (Object[] row : data) {
            ClubRoleType role = (ClubRoleType) row[0];
            Long count = (Long) row[1];
            if (role != null) {
                result.put(role.name(), count);
            }
        }
        
        return result;
    }

    /**
     * Top 5 CLB có nhiều thành viên nhất
     */
    public List<ClubStatistic> getTop5ClubsByMembers() {
        List<Object[]> data = registerRepository.findTopClubsByMemberCount(JoinStatus.DaDuyet, true);
        
        return data.stream()
                .limit(5)
                .map(row -> ClubStatistic.builder()
                        .clubId((Integer) row[0])
                        .clubName((String) row[1])
                        .clubLogo((String) row[2])
                        .category(row[3] != null ? ((ClubCategory) row[3]).name() : null)
                        .memberCount((Long) row[4])
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Danh sách CLB mới trong tháng
     */
    public List<ClubResponse> getNewClubsThisMonth() {
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        List<Clubs> newClubs = clubRepository.findNewClubsThisMonth(startOfMonth);
        
        return newClubs.stream()
                .map(clubMapper::toResponse)
                .collect(Collectors.toList());
    }
}

