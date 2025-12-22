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

/**
 * AdminDashboardService - Service xử lý logic nghiệp vụ cho Admin Dashboard
 * 
 * Service này cung cấp các thống kê và dữ liệu tổng quan cho Admin Dashboard:
 * - Tổng số CLB đang hoạt động
 * - Tổng số thành viên (đã duyệt và đã thanh toán)
 * - Tổng số sinh viên duy nhất tham gia CLB
 * - Thống kê CLB theo danh mục (category)
 * - Thống kê thành viên theo vai trò (ClubRoleType)
 * - Top 5 CLB có nhiều thành viên nhất
 * - Danh sách CLB mới thành lập trong tháng
 * 
 * Tất cả các method trong service này đều dành cho Admin (kiểm tra quyền ở Controller layer).
 * 
 * @Service - Đánh dấu đây là một Spring Service, được quản lý bởi Spring Container
 * @RequiredArgsConstructor - Lombok tự động tạo constructor với các field final để dependency injection
 * @FieldDefaults - Lombok: tất cả field là PRIVATE và FINAL (immutable dependencies)
 * @Slf4j - Lombok: tự động tạo logger với tên "log" để ghi log
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AdminDashboardService {
    /**
     * Repository để truy vấn và thao tác với bảng Clubs trong database
     */
    ClubRepository clubRepository;
    
    /**
     * Repository để truy vấn và thao tác với bảng Registers trong database
     * Dùng để đếm thành viên, thống kê theo role...
     */
    RegisterRepository registerRepository;
    
    /**
     * Repository để truy vấn và thao tác với bảng Users trong database
     */
    UserRepository userRepository;
    
    /**
     * Mapper để chuyển đổi giữa Entity (Clubs) và DTO (ClubResponse)
     */
    ClubMapper clubMapper;

    /**
     * Lấy tất cả dữ liệu tổng quan cho Admin Dashboard
     * 
     * Phương thức này tổng hợp tất cả các thống kê cần thiết cho Admin Dashboard
     * vào một response object duy nhất, giúp Frontend có thể hiển thị dashboard ngay lập tức.
     * 
     * @return AdminDashboardResponse - Object chứa tất cả thống kê: tổng số CLB, thành viên, 
     *                                  thống kê theo category, role, top 5 CLB, CLB mới...
     * 
     * Lưu ý: Method này gọi nhiều method khác, có thể tốn thời gian nếu data lớn
     *        Có thể cần optimize bằng caching nếu cần thiết
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
     * 
     * Phương thức này đếm số lượng CLB có isActive = true trong hệ thống.
     * 
     * @return Long - Tổng số CLB đang hoạt động
     */
    public Long getTotalClubs() {
        return clubRepository.countByIsActiveTrue();
    }

    /**
     * Tổng số thành viên (đã duyệt + đã thanh toán)
     * 
     * Phương thức này đếm số lượng đơn đăng ký có status = DaDuyet và isPaid = true.
     * 
     * Lưu ý quan trọng:
     * - 1 sinh viên tham gia nhiều CLB = nhiều membership (số lượng sẽ lớn hơn số sinh viên thực tế)
     * - Ví dụ: Sinh viên A tham gia 2 CLB → đếm là 2 memberships
     * - Nếu muốn số lượng sinh viên duy nhất, dùng getTotalStudents()
     * 
     * @return Long - Tổng số membership (một user có thể có nhiều membership)
     */
    public Long getTotalMembers() {
        return registerRepository.countByStatusAndIsPaid(JoinStatus.DaDuyet, true);
    }

    /**
     * Tổng số sinh viên duy nhất tham gia CLB
     * 
     * Phương thức này đếm số lượng user duy nhất (không trùng lặp) đã tham gia CLB.
     * Khác với getTotalMembers(), method này đếm số user, không đếm số membership.
     * 
     * Ví dụ:
     * - User A tham gia 2 CLB → getTotalMembers() = 2, getTotalStudents() = 1
     * - User B tham gia 1 CLB → getTotalMembers() = 3, getTotalStudents() = 2
     * 
     * @return Long - Tổng số sinh viên duy nhất (mỗi user chỉ đếm 1 lần)
     */
    public Long getTotalStudents() {
        return registerRepository.countDistinctStudents(JoinStatus.DaDuyet, true);
    }

    /**
     * Thống kê số lượng CLB theo danh mục (category)
     * 
     * Phương thức này đếm số lượng CLB trong mỗi danh mục (Học thuật, Thể thao, Nghệ thuật...).
     * Kết quả là một Map với key = tên category (enum name), value = số lượng CLB.
     * 
     * Đặc biệt:
     * - Tất cả các category đều được khởi tạo với giá trị 0 (kể cả category không có CLB)
     * - Đảm bảo Frontend luôn có đầy đủ data để hiển thị chart/graph
     * 
     * @return Map<String, Long> - Map chứa số lượng CLB theo từng category
     *                             Key: Tên category (ví dụ: "HocThuat", "TheThao")
     *                             Value: Số lượng CLB trong category đó
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
     * Thống kê số lượng thành viên theo vai trò (ClubRoleType)
     * 
     * Phương thức này đếm số lượng thành viên trong mỗi vai trò (ChuTich, PhoChuTich, ThuKy, ThanhVien).
     * Kết quả là một Map với key = tên role (enum name), value = số lượng thành viên.
     * 
     * Điều kiện để được đếm:
     * - status = DaDuyet (đã được duyệt)
     * - isPaid = true (đã thanh toán)
     * 
     * Đặc biệt:
     * - Tất cả các role đều được khởi tạo với giá trị 0 (kể cả role không có thành viên)
     * - Đảm bảo Frontend luôn có đầy đủ data để hiển thị chart/graph
     * 
     * @return Map<String, Long> - Map chứa số lượng thành viên theo từng role
     *                             Key: Tên role (ví dụ: "ChuTich", "PhoChuTich", "ThanhVien")
     *                             Value: Số lượng thành viên có role đó
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
     * Lấy Top 5 CLB có nhiều thành viên nhất
     * 
     * Phương thức này tìm 5 CLB có số lượng thành viên (membership) nhiều nhất.
     * Thành viên được đếm phải có status = DaDuyet và isPaid = true.
     * 
     * Kết quả được sắp xếp theo số lượng thành viên giảm dần (CLB nhiều thành viên nhất trước).
     * 
     * @return List<ClubStatistic> - Danh sách top 5 CLB, mỗi item chứa:
     *                               - clubId, clubName, clubLogo, category
     *                               - memberCount (số lượng thành viên)
     * 
     * Lưu ý: Chỉ lấy top 5 (limit 5), nếu có nhiều hơn 5 CLB, chỉ lấy 5 CLB đầu tiên
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
     * Lấy danh sách CLB mới thành lập trong tháng hiện tại
     * 
     * Phương thức này tìm tất cả CLB được thành lập trong tháng hiện tại
     * (establishedDate >= ngày đầu tháng và <= ngày cuối tháng).
     * 
     * CLB được coi là "mới" nếu establishedDate nằm trong tháng hiện tại.
     * 
     * @return List<ClubResponse> - Danh sách CLB mới thành lập trong tháng, đã được map sang DTO
     * 
     * Lưu ý: Sử dụng LocalDate để so sánh (chỉ so sánh ngày, không so sánh giờ)
     */
    public List<ClubResponse> getNewClubsThisMonth() {
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        List<Clubs> newClubs = clubRepository.findNewClubsThisMonth(startOfMonth);
        
        return newClubs.stream()
                .map(clubMapper::toResponse)
                .collect(Collectors.toList());
    }
}

