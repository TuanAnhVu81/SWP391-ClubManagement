package com.swp391.clubmanagement.service;

import com.swp391.clubmanagement.dto.request.ClubUpdateRequest;
import com.swp391.clubmanagement.dto.response.ClubMemberResponse;
import com.swp391.clubmanagement.dto.response.ClubResponse;
import com.swp391.clubmanagement.dto.response.ClubStatsResponse;
import com.swp391.clubmanagement.dto.response.JoinedClubResponse;
import com.swp391.clubmanagement.entity.ClubApplications;
import com.swp391.clubmanagement.entity.Clubs;
import com.swp391.clubmanagement.entity.Memberships;
import com.swp391.clubmanagement.entity.Registers;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.enums.ClubCategory;
import com.swp391.clubmanagement.enums.ClubRoleType;
import com.swp391.clubmanagement.enums.JoinStatus;
import com.swp391.clubmanagement.exception.AppException;
import com.swp391.clubmanagement.exception.ErrorCode;
import com.swp391.clubmanagement.mapper.ClubMapper;
import com.swp391.clubmanagement.repository.ClubApplicationRepository;
import com.swp391.clubmanagement.repository.ClubRepository;
import com.swp391.clubmanagement.repository.MembershipRepository;
import com.swp391.clubmanagement.repository.RegisterRepository;
import com.swp391.clubmanagement.repository.RoleRepository;
import com.swp391.clubmanagement.repository.UserRepository;
import com.swp391.clubmanagement.enums.RoleType;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ClubService - Service xử lý logic nghiệp vụ cho quản lý Câu lạc bộ (CLB)
 * 
 * Service này chịu trách nhiệm quản lý toàn bộ vòng đời và thông tin của CLB:
 * - Xem danh sách CLB (public, có thể search và filter)
 * - Xem chi tiết CLB (public)
 * - Cập nhật thông tin CLB (leader only: logo, mô tả, địa điểm)
 * - Xem danh sách thành viên CLB (public)
 * - Thống kê CLB (leader only: số thành viên, doanh thu, thành viên chưa đóng phí)
 * - Xem CLB mà user đã tham gia (với thông tin membership và trạng thái)
 * - Xóa CLB (admin only: soft delete với cascade các bản ghi liên quan)
 * 
 * Tính năng đặc biệt:
 * - Tự động kiểm tra và cập nhật status membership hết hạn (lazy evaluation)
 * - Hỗ trợ search và filter CLB theo tên và category
 * - Thống kê chi tiết cho leader (số thành viên, doanh thu, phân loại theo role...)
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
public class ClubService {
    /**
     * Repository để truy vấn và thao tác với bảng Clubs trong database
     */
    ClubRepository clubRepository;
    
    /**
     * Repository để truy vấn và thao tác với bảng ClubApplications trong database
     * Dùng để xóa đơn đăng ký thành lập CLB khi xóa CLB
     */
    ClubApplicationRepository clubApplicationRepository;
    
    /**
     * Repository để truy vấn và thao tác với bảng Memberships trong database
     * Dùng để xóa các gói membership khi xóa CLB
     */
    MembershipRepository membershipRepository;
    
    /**
     * Repository để truy vấn và thao tác với bảng Registers trong database
     * Dùng để đếm thành viên, lấy danh sách thành viên, thống kê...
     */
    RegisterRepository registerRepository;
    
    /**
     * Repository để truy vấn và thao tác với bảng Users trong database
     * Dùng để lấy thông tin user hiện tại (leader/admin)
     */
    UserRepository userRepository;
    
    /**
     * Repository để truy vấn và thao tác với bảng Roles trong database
     * Dùng để chuyển role của founder về SinhVien khi xóa CLB
     */
    RoleRepository roleRepository;
    
    /**
     * Mapper để chuyển đổi giữa Entity (Clubs, Registers) và DTO (ClubResponse, ClubMemberResponse...)
     */
    ClubMapper clubMapper;
    
    /**
     * Helper: Kiểm tra và tự động cập nhật status nếu membership đã hết hạn
     * (Lazy Evaluation - chỉ check khi cần)
     * 
     * Phương thức này được gọi khi cần kiểm tra membership có hết hạn không.
     * Thay vì dùng scheduled job để check định kỳ, ta check khi user request data (lazy evaluation).
     * 
     * Logic:
     * - Chỉ check các register có: status = DaDuyet, isPaid = true, có endDate
     * - Nếu endDate < now → Update status thành HetHan
     * - Ghi log chi tiết để tracking
     * 
     * Ưu điểm của lazy evaluation:
     * - Không cần chạy scheduled job tốn tài nguyên
     * - Chỉ update khi thực sự cần thiết (khi user xem data)
     * - Đảm bảo data được update đúng lúc user cần
     * 
     * @param register - Register cần kiểm tra
     * @return true nếu đã update status thành HetHan, false nếu chưa hết hạn hoặc không cần update
     */
    private boolean checkAndUpdateExpiry(Registers register) {
        LocalDateTime now = LocalDateTime.now();
        
        // Log để debug
        log.debug("Checking expiry for subscription {}: status={}, isPaid={}, endDate={}, now={}", 
                register.getSubscriptionId(),
                register.getStatus(),
                register.getIsPaid(),
                register.getEndDate(),
                now);
        
        // Chỉ check nếu: status = DaDuyet + đã thanh toán + có endDate
        if (register.getStatus() == JoinStatus.DaDuyet 
            && register.getIsPaid() != null && register.getIsPaid()
            && register.getEndDate() != null
            && register.getEndDate().isBefore(now)) {
            
            // Update status thành HetHan
            register.setStatus(JoinStatus.HetHan);
            registerRepository.save(register);
            
            log.info("✅ Auto-updated subscription {} to HetHan. User: {}, Club: {}, EndDate: {} < Now: {}", 
                    register.getSubscriptionId(),
                    register.getUser().getEmail(),
                    register.getMembershipPackage().getClub().getClubName(),
                    register.getEndDate(),
                    now);
            
            return true;
        }
        
        // Log lý do không update
        if (register.getStatus() != JoinStatus.DaDuyet) {
            log.debug("❌ Skip: Status is not DaDuyet (current: {})", register.getStatus());
        } else if (register.getIsPaid() == null || !register.getIsPaid()) {
            log.debug("❌ Skip: Not paid yet");
        } else if (register.getEndDate() == null) {
            log.debug("❌ Skip: No endDate");
        } else if (!register.getEndDate().isBefore(now)) {
            log.debug("❌ Skip: Not expired yet (endDate: {} >= now: {})", register.getEndDate(), now);
        }
        
        return false;
    }
    
    /**
     * Helper: Kiểm tra và update expiry cho danh sách registers
     * 
     * @param registers Danh sách cần kiểm tra
     */
    private void checkAndUpdateExpiryBatch(List<Registers> registers) {
        int updatedCount = 0;
        for (Registers register : registers) {
            if (checkAndUpdateExpiry(register)) {
                updatedCount++;
            }
        }
        if (updatedCount > 0) {
            log.info("Auto-updated {} expired memberships to HetHan", updatedCount);
        }
    }
    
    /**
     * Lấy danh sách tất cả CLB đang hoạt động (Public - không cần authentication)
     * 
     * Phương thức này cho phép user xem danh sách CLB trong hệ thống.
     * Có thể search theo tên và filter theo category để tìm kiếm dễ dàng hơn.
     * 
     * @param name - Tên CLB cần tìm (search, có thể là substring). Null = không filter theo tên
     * @param category - Danh mục CLB cần filter (Học thuật, Thể thao, Nghệ thuật...). Null = không filter
     * @return List<ClubResponse> - Danh sách CLB đã được map sang DTO, kèm theo tổng số thành viên
     * 
     * Lưu ý:
     * - Chỉ trả về các CLB đang active (isActive = true)
     * - Tự động đếm tổng số thành viên chính thức (DaDuyet + isPaid = true) cho mỗi CLB
     * - Public endpoint: Không cần authentication
     */
    public List<ClubResponse> getAllClubs(String name, ClubCategory category) {
        List<Clubs> clubs;
        
        if (name != null || category != null) {
            clubs = clubRepository.searchByNameAndCategory(name, category);
        } else {
            clubs = clubRepository.findByIsActiveTrue();
        }
        
        return clubs.stream()
                .map(club -> {
                    ClubResponse response = clubMapper.toResponse(club);
                    // Đếm tổng số thành viên chính thức (đã duyệt và đã đóng phí)
                    long totalMembers = registerRepository.countByMembershipPackage_Club_ClubIdAndStatusAndIsPaid(
                            club.getClubId(), JoinStatus.DaDuyet, true);
                    response.setTotalMembers(totalMembers);
                    return response;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Xem chi tiết thông tin 1 CLB (Public - không cần authentication)
     * 
     * Phương thức này cho phép user xem chi tiết thông tin của một CLB cụ thể,
     * bao gồm tên, mô tả, logo, địa điểm, email, founder, và tổng số thành viên.
     * 
     * @param clubId - ID của CLB cần xem chi tiết
     * @return ClubResponse - Thông tin chi tiết CLB đã được map sang DTO, kèm tổng số thành viên
     * @throws AppException với ErrorCode.CLUB_NOT_FOUND nếu không tìm thấy CLB
     * 
     * Lưu ý:
     * - Public endpoint: Không cần authentication, ai cũng có thể xem
     * - Tự động đếm tổng số thành viên chính thức (DaDuyet + isPaid = true)
     */
    public ClubResponse getClubById(Integer clubId) {
        Clubs club = clubRepository.findById(clubId)
                .orElseThrow(() -> new AppException(ErrorCode.CLUB_NOT_FOUND));
        
        ClubResponse response = clubMapper.toResponse(club);
        
        // Đếm tổng số thành viên chính thức (đã duyệt và đã đóng phí)
        long totalMembers = registerRepository.countByMembershipPackage_Club_ClubIdAndStatusAndIsPaid(
                clubId, JoinStatus.DaDuyet, true);
        response.setTotalMembers(totalMembers);
        
        return response;
    }
    
    /**
     * Cập nhật thông tin CLB - Logo, mô tả, địa điểm sinh hoạt (Leader only)
     * 
     * Phương thức này cho phép Leader (founder) của CLB cập nhật một số thông tin cơ bản:
     * - Logo: URL hình ảnh logo của CLB
     * - Mô tả: Mô tả chi tiết về CLB, hoạt động, mục tiêu...
     * - Địa điểm: Nơi sinh hoạt của CLB (phòng, tòa nhà, khu vực...)
     * 
     * Business rules:
     * - Chỉ founder của CLB mới được cập nhật (check bằng founder.userId)
     * - Các thông tin khác như tên CLB, category, email không thể thay đổi (cần qua đơn đăng ký)
     * 
     * @param clubId - ID của CLB cần cập nhật
     * @param request - DTO chứa thông tin cần cập nhật (logo, description, location)
     * @return ClubResponse - Thông tin CLB sau khi được cập nhật
     * @throws AppException với ErrorCode.CLUB_NOT_FOUND nếu không tìm thấy CLB
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user hiện tại
     * @throws AppException với ErrorCode.NOT_CLUB_LEADER nếu user không phải founder
     * 
     * @Transactional - Đảm bảo toàn bộ operations được thực hiện trong một transaction
     */
    @Transactional
    public ClubResponse updateClub(Integer clubId, ClubUpdateRequest request) {
        // Lấy thông tin user hiện tại
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Users currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        // Tìm CLB
        Clubs club = clubRepository.findById(clubId)
                .orElseThrow(() -> new AppException(ErrorCode.CLUB_NOT_FOUND));
        
        // Kiểm tra quyền: chỉ founder mới được cập nhật
        // (Trong thực tế, có thể check thêm ClubRoleType.Leader trong bảng Registers)
        if (!club.getFounder().getUserId().equals(currentUser.getUserId())) {
            throw new AppException(ErrorCode.NOT_CLUB_LEADER);
        }
        
        // Cập nhật thông tin
        if (request.getLogo() != null) {
            club.setLogo(request.getLogo());
        }
        
        if (request.getDescription() != null) {
            club.setDescription(request.getDescription());
        }
        
        if (request.getLocation() != null) {
            club.setLocation(request.getLocation());
        }
        
        club = clubRepository.save(club);
        log.info("Club {} updated by leader {}", clubId, currentUser.getEmail());
        
        return clubMapper.toResponse(club);
    }
    
    /**
     * Xem danh sách thành viên của CLB (Public - không cần authentication)
     * 
     * Phương thức này cho phép user xem danh sách thành viên chính thức của một CLB.
     * Chỉ hiển thị những thành viên đã được duyệt và đã thanh toán phí membership.
     * 
     * Thông tin hiển thị cho mỗi thành viên:
     * - Thông tin cá nhân: tên, mã sinh viên, avatar
     * - Vai trò trong CLB: ChuTich, PhoChuTich, ThuKy, ThanhVien
     * - Thông tin membership: gói đã đăng ký, ngày tham gia
     * 
     * @param clubId - ID của CLB cần xem danh sách thành viên
     * @return List<ClubMemberResponse> - Danh sách thành viên đã được map sang DTO
     * @throws AppException với ErrorCode.CLUB_NOT_FOUND nếu không tìm thấy CLB
     * 
     * Lưu ý:
     * - Public endpoint: Không cần authentication, ai cũng có thể xem
     * - Chỉ hiển thị thành viên có: status = DaDuyet và isPaid = true
     * - Không hiển thị những đơn đăng ký đang chờ duyệt hoặc đã bị từ chối
     */
    public List<ClubMemberResponse> getClubMembers(Integer clubId) {
        // Kiểm tra CLB có tồn tại không
        if (!clubRepository.existsById(clubId)) {
            throw new AppException(ErrorCode.CLUB_NOT_FOUND);
        }
        
        // Lấy danh sách thành viên đã được duyệt và đã đóng phí
        List<Registers> registers = registerRepository.findByMembershipPackage_Club_ClubIdAndStatus(clubId, JoinStatus.DaDuyet);
        
        log.debug("Found {} registers for club {} with status DaDuyet", registers.size(), clubId);
        
        return registers.stream()
                .filter(r -> {
                    boolean isPaid = r.getIsPaid();
                    log.debug("Register {}: userId={}, clubRole={}, isPaid={}", 
                            r.getSubscriptionId(), r.getUser().getUserId(), r.getClubRole(), isPaid);
                    return isPaid;
                }) // Chỉ lấy những người đã đóng phí
                .map(clubMapper::toMemberResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Thống kê nội bộ CLB (Leader only)
     * 
     * Phương thức này cung cấp thống kê chi tiết về CLB cho Leader:
     * - Tổng số thành viên (đã duyệt và đã thanh toán)
     * - Số đơn đăng ký đang chờ duyệt (ChoDuyet)
     * - Số đơn đăng ký đã bị từ chối (TuChoi)
     * - Phân loại thành viên theo role: ChuTich, PhoChuTich, ThuKy, ThanhVien
     * - Doanh thu: Tổng phí membership đã thu trong tháng hiện tại (không tính founder)
     * - Số thành viên đã thanh toán và chưa thanh toán
     * - Danh sách chi tiết thành viên chưa đóng phí (để nhắc nhở)
     * 
     * Business rules:
     * - Chỉ Leader (ChuTich, PhoChuTich) hoặc Founder mới được xem thống kê
     * - Doanh thu chỉ tính trong tháng hiện tại (theo paymentDate)
     * - Không tính phí của founder (founder được miễn phí)
     * 
     * @param clubId - ID của CLB cần xem thống kê
     * @return ClubStatsResponse - Object chứa tất cả thống kê chi tiết
     * @throws AppException với ErrorCode.CLUB_NOT_FOUND nếu không tìm thấy CLB
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user hiện tại
     * @throws AppException với ErrorCode.NOT_CLUB_LEADER nếu user không phải leader/founder
     * 
     * Lưu ý:
     * - Phải là Leader hoặc Founder mới được gọi method này
     * - Doanh thu tính theo tháng hiện tại, không phải tổng từ trước đến nay
     */
    public ClubStatsResponse getClubStats(Integer clubId) {
        // Lấy user hiện tại
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Users currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        // Kiểm tra CLB có tồn tại
        Clubs club = clubRepository.findById(clubId)
                .orElseThrow(() -> new AppException(ErrorCode.CLUB_NOT_FOUND));
        
        // Kiểm tra quyền: Phải là Leader hoặc Founder
        boolean isLeader = registerRepository.existsByUserAndMembershipPackage_Club_ClubIdAndClubRoleInAndStatusAndIsPaid(
                currentUser,
                clubId,
                Arrays.asList(ClubRoleType.ChuTich, ClubRoleType.PhoChuTich),
                JoinStatus.DaDuyet,
                true
        );
        
        if (!isLeader && !club.getFounder().getUserId().equals(currentUser.getUserId())) {
            throw new AppException(ErrorCode.NOT_CLUB_LEADER);
        }
        
        // Lấy tất cả đăng ký của CLB
        List<Registers> allRegisters = registerRepository.findByMembershipPackage_Club_ClubId(clubId);
        
        // Thống kê thành viên
        long totalMembers = allRegisters.stream()
                .filter(r -> r.getStatus() == JoinStatus.DaDuyet && r.getIsPaid())
                .count();
        
        long pendingCount = allRegisters.stream()
                .filter(r -> r.getStatus() == JoinStatus.ChoDuyet)
                .count();
        
        long rejectedCount = allRegisters.stream()
                .filter(r -> r.getStatus() == JoinStatus.TuChoi)
                .count();
        
        // Thống kê theo vai trò
        List<Registers> activeMembers = allRegisters.stream()
                .filter(r -> r.getStatus() == JoinStatus.DaDuyet && r.getIsPaid())
                .collect(Collectors.toList());
        
        long chuTichCount = activeMembers.stream()
                .filter(r -> r.getClubRole() == ClubRoleType.ChuTich)
                .count();
        
        long phoChuTichCount = activeMembers.stream()
                .filter(r -> r.getClubRole() == ClubRoleType.PhoChuTich)
                .count();
        
        long thuKyCount = activeMembers.stream()
                .filter(r -> r.getClubRole() == ClubRoleType.ThuKy)
                .count();
        
        long thanhVienCount = activeMembers.stream()
                .filter(r -> r.getClubRole() == ClubRoleType.ThanhVien)
                .count();
        
        // Thống kê tài chính - Tính doanh thu theo tháng (chỉ tính những người đã trả tiền, trừ founder)
        Users founder = club.getFounder();
        YearMonth currentMonth = YearMonth.now();
        BigDecimal totalRevenue = allRegisters.stream()
                .filter(r -> r.getIsPaid() && r.getPaymentDate() != null)
                .filter(r -> {
                    // Loại trừ tiền của founder
                    if (founder != null && r.getUser().getUserId().equals(founder.getUserId())) {
                        return false;
                    }
                    // Chỉ tính thanh toán trong tháng hiện tại
                    LocalDateTime paymentDate = r.getPaymentDate();
                    YearMonth paymentMonth = YearMonth.from(paymentDate);
                    return paymentMonth.equals(currentMonth);
                })
                .map(r -> r.getMembershipPackage().getPrice())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        long paidCount = allRegisters.stream()
                .filter(r -> r.getStatus() == JoinStatus.DaDuyet && r.getIsPaid())
                .count();
        
        long unpaidCount = allRegisters.stream()
                .filter(r -> r.getStatus() == JoinStatus.DaDuyet && !r.getIsPaid())
                .count();
        
        // Danh sách chưa đóng phí
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        List<ClubStatsResponse.UnpaidMemberInfo> unpaidMembers = allRegisters.stream()
                .filter(r -> r.getStatus() == JoinStatus.DaDuyet && !r.getIsPaid())
                .map(r -> ClubStatsResponse.UnpaidMemberInfo.builder()
                        .subscriptionId(r.getSubscriptionId())
                        .studentCode(r.getUser().getStudentCode())
                        .fullName(r.getUser().getFullName())
                        .packageName(r.getMembershipPackage().getPackageName())
                        .packagePrice(r.getMembershipPackage().getPrice())
                        .joinDate(r.getJoinDate() != null ? r.getJoinDate().format(formatter) : null)
                        .build())
                .collect(Collectors.toList());
        
        return ClubStatsResponse.builder()
                .clubId(clubId)
                .clubName(club.getClubName())
                .totalMembers(totalMembers)
                .pendingRegistrations(pendingCount)
                .rejectedRegistrations(rejectedCount)
                .chuTichCount(chuTichCount)
                .phoChuTichCount(phoChuTichCount)
                .thuKyCount(thuKyCount)
                .thanhVienCount(thanhVienCount)
                .totalRevenue(totalRevenue)
                .paidCount(paidCount)
                .unpaidCount(unpaidCount)
                .unpaidMembers(unpaidMembers)
                .build();
    }
    
    /**
     * Lấy danh sách CLB mà student đã tham gia (bao gồm đang hoạt động và hết hạn)
     * @param userId ID của user (student)
     * @return Danh sách CLB mà user đã tham gia (DaDuyet hoặc HetHan)
     */
    public List<JoinedClubResponse> getJoinedClubsByUser(String userId) {
        // Lấy user
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        // Lấy tất cả đăng ký của user
        List<Registers> allRegisters = registerRepository.findByUser(user);
        
        // Lazy evaluation: Check và update expiry trước
        checkAndUpdateExpiryBatch(allRegisters);
        
        // ✅ QUAN TRỌNG: Re-fetch từ DB sau khi update để có status mới
        allRegisters = registerRepository.findByUser(user);
        
        // Filter: Chỉ lấy DaDuyet (đã thanh toán) hoặc HetHan
        List<Registers> registers = allRegisters.stream()
                .filter(r -> {
                    // DaDuyet + đã thanh toán HOẶC HetHan
                    if (r.getStatus() == JoinStatus.DaDuyet && r.getIsPaid()) {
                        return true;
                    }
                    return r.getStatus() == JoinStatus.HetHan;
                })
                .collect(Collectors.toList());
        
        // Log để debug
        log.debug("Found {} registers after filtering (DaDuyet paid or HetHan)", registers.size());
        
        // Chuyển đổi sang JoinedClubResponse
        return registers.stream()
                .map(register -> {
                    Clubs club = register.getMembershipPackage().getClub();
                    ClubResponse clubResponse = clubMapper.toResponse(club);
                    
                    // Tính isExpired: endDate < now
                    boolean isExpired = register.getEndDate() != null 
                            && register.getEndDate().isBefore(LocalDateTime.now());
                    
                    // Tính canRenew: status = HetHan
                    boolean canRenew = register.getStatus() == JoinStatus.HetHan;
                    
                    // Log để debug
                    log.debug("Register {}: status={}, endDate={}, isExpired={}, canRenew={}", 
                            register.getSubscriptionId(), 
                            register.getStatus(), 
                            register.getEndDate(),
                            isExpired, 
                            canRenew);
                    
                    return JoinedClubResponse.builder()
                            .clubId(clubResponse.getClubId())
                            .clubName(clubResponse.getClubName())
                            .category(clubResponse.getCategory())
                            .logo(clubResponse.getLogo())
                            .location(clubResponse.getLocation())
                            .description(clubResponse.getDescription())
                            .email(clubResponse.getEmail())
                            .isActive(clubResponse.getIsActive())
                            .establishedDate(clubResponse.getEstablishedDate())
                            .founderId(clubResponse.getFounderId())
                            .founderName(clubResponse.getFounderName())
                            .founderStudentCode(clubResponse.getFounderStudentCode())
                            // Thêm các field mới cho gia hạn
                            .subscriptionId(register.getSubscriptionId())
                            .packageId(register.getMembershipPackage().getPackageId())
                            .packageName(register.getMembershipPackage().getPackageName())
                            .clubRole(register.getClubRole())
                            .joinedAt(register.getJoinDate())
                            .endDate(register.getEndDate())
                            .canRenew(canRenew)
                            .isExpired(isExpired)
                            .build();
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Xóa CLB (Admin only)
     * - Tìm tất cả members của club
     * - Chuyển Chủ tịch về role SinhVien
     * - Xóa tất cả registrations của club
     * - Xóa tất cả membership packages của club
     * - Xóa tất cả club applications liên quan
     * - Xóa club
     * 
     * Thứ tự xóa quan trọng để tránh foreign key constraint:
     * 1. Registrations (FK -> Memberships)
     * 2. Memberships (FK -> Clubs)
     * 3. ClubApplications (FK -> Clubs)
     * 4. Clubs
     */
    @Transactional
    public void deleteClub(Integer clubId) {
        // Tìm CLB
        Clubs club = clubRepository.findById(clubId)
                .orElseThrow(() -> new AppException(ErrorCode.CLUB_NOT_FOUND));
        
        // Lấy tất cả registrations của CLB
        List<Registers> allRegistrations = registerRepository.findByMembershipPackage_Club_ClubId(clubId);
        
        log.info("🗑️ Deleting club {} ({}) with {} registrations", 
                clubId, club.getClubName(), allRegistrations.size());
        
        // Bước 1: Tìm Chủ tịch của CLB (nếu có) và chuyển về SinhVien
        List<Registers> presidentRegistrations = allRegistrations.stream()
                .filter(r -> r.getClubRole() == ClubRoleType.ChuTich)
                .filter(r -> r.getStatus() == JoinStatus.DaDuyet)
                .filter(r -> r.getIsPaid() != null && r.getIsPaid())
                .collect(Collectors.toList());
        
        // Chuyển tất cả Chủ tịch về role SinhVien
        for (Registers presidentReg : presidentRegistrations) {
            Users president = presidentReg.getUser();
            
            // Kiểm tra role hiện tại của president
            if (president.getRole().getRoleName() == RoleType.ChuTich) {
                // Chuyển về SinhVien
                var sinhVienRole = roleRepository.findByRoleName(RoleType.SinhVien)
                        .orElseThrow(() -> {
                            log.error("SinhVien role not found in database. Please check ApplicationInitConfig.");
                            return new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
                        });
                
                president.setRole(sinhVienRole);
                userRepository.save(president);
                
                log.info("✅ Changed president {} role from ChuTich to SinhVien (club {} is being deleted)", 
                        president.getEmail(), clubId);
            }
        }
        
        // Bước 2: Xóa tất cả registrations của club (FK -> Memberships)
        registerRepository.deleteAll(allRegistrations);
        log.info("✅ Deleted {} registrations for club {}", allRegistrations.size(), clubId);
        
        // Bước 3: Xóa tất cả membership packages của club (FK -> Clubs)
        List<Memberships> allMemberships = membershipRepository.findByClub_ClubId(clubId);
        membershipRepository.deleteAll(allMemberships);
        log.info("✅ Deleted {} membership packages for club {}", allMemberships.size(), clubId);
        
        // Bước 4: Xóa tất cả club applications liên quan (FK -> Clubs)
        List<ClubApplications> allApplications = clubApplicationRepository.findByClub(club);
        clubApplicationRepository.deleteAll(allApplications);
        log.info("✅ Deleted {} club applications for club {}", allApplications.size(), clubId);
        
        // Bước 5: Xóa club
        clubRepository.delete(club);
        log.info("✅ Successfully deleted club {} ({})", clubId, club.getClubName());
    }
}
