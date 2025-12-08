package com.swp391.clubmanagement.service;

import com.swp391.clubmanagement.dto.response.AdminDashboardResponse;
import com.swp391.clubmanagement.dto.response.ClubResponse;
import com.swp391.clubmanagement.dto.response.ClubStatistic;
import com.swp391.clubmanagement.entity.Clubs;
import com.swp391.clubmanagement.enums.ClubCategory;
import com.swp391.clubmanagement.enums.ClubRoleType;
import com.swp391.clubmanagement.enums.JoinStatus;
import com.swp391.clubmanagement.mapper.ClubMapper;
import com.swp391.clubmanagement.repository.ClubRepository;
import com.swp391.clubmanagement.repository.RegisterRepository;
import com.swp391.clubmanagement.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AdminDashboardService {
    ClubRepository clubRepository;
    RegisterRepository registerRepository;
    UserRepository userRepository;
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

