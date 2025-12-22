package com.swp391.clubmanagement.service;

import com.swp391.clubmanagement.dto.request.ClubApplicationRequest;
import com.swp391.clubmanagement.dto.request.ReviewApplicationRequest;
import com.swp391.clubmanagement.dto.response.ClubApplicationResponse;
import com.swp391.clubmanagement.entity.ClubApplications;
import com.swp391.clubmanagement.entity.Clubs;
import com.swp391.clubmanagement.entity.Memberships;
import com.swp391.clubmanagement.entity.Registers;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.enums.ClubRoleType;
import com.swp391.clubmanagement.enums.JoinStatus;
import com.swp391.clubmanagement.enums.RequestStatus;
import com.swp391.clubmanagement.exception.AppException;
import com.swp391.clubmanagement.exception.ErrorCode;
import com.swp391.clubmanagement.mapper.ClubApplicationMapper;
import com.swp391.clubmanagement.enums.RoleType;
import com.swp391.clubmanagement.repository.ClubApplicationRepository;
import com.swp391.clubmanagement.repository.ClubRepository;
import com.swp391.clubmanagement.repository.MembershipRepository;
import com.swp391.clubmanagement.repository.RegisterRepository;
import com.swp391.clubmanagement.repository.RoleRepository;
import com.swp391.clubmanagement.repository.UserRepository;
import com.swp391.clubmanagement.utils.DateTimeUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ClubApplicationService - Service xử lý logic nghiệp vụ cho đơn đăng ký thành lập CLB
 * 
 * Service này chịu trách nhiệm quản lý toàn bộ vòng đời của một đơn đăng ký thành lập CLB:
 * - Tạo đơn mới (bởi Student)
 * - Xem danh sách đơn (bởi Admin hoặc Student)
 * - Duyệt/từ chối đơn (bởi Admin)
 * - Tự động tạo CLB và setup ban đầu khi đơn được duyệt
 * 
 * Khi một đơn được duyệt (ChapThuan), hệ thống sẽ tự động:
 * 1. Tạo một CLB mới với thông tin từ đơn
 * 2. Tạo gói membership mặc định cho CLB đó
 * 3. Thêm founder vào CLB với role ChuTich (Chủ tịch)
 * 4. Cập nhật role của founder trong bảng Users
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
public class ClubApplicationService {
    
    /**
     * Repository để truy vấn và thao tác với bảng ClubApplications trong database
     * Chứa các đơn đăng ký thành lập CLB
     */
    ClubApplicationRepository clubApplicationRepository;
    
    /**
     * Repository để truy vấn và thao tác với bảng Clubs trong database
     * CLB được tạo ra sau khi đơn được duyệt
     */
    ClubRepository clubRepository;
    
    /**
     * Repository để truy vấn và thao tác với bảng Memberships trong database
     * Chứa các gói membership của các CLB (ví dụ: Thành viên cơ bản, VIP...)
     */
    MembershipRepository membershipRepository;
    
    /**
     * Repository để truy vấn và thao tác với bảng Registers trong database
     * Chứa thông tin đăng ký tham gia CLB của các user (status, payment, role trong CLB...)
     */
    RegisterRepository registerRepository;
    
    /**
     * Repository để truy vấn và thao tác với bảng Users trong database
     * Chứa thông tin tài khoản người dùng
     */
    UserRepository userRepository;
    
    /**
     * Repository để truy vấn và thao tác với bảng Roles trong database
     * Chứa các role của hệ thống (SinhVien, Admin, ChuTich, PhoChuTich...)
     */
    RoleRepository roleRepository;
    
    /**
     * Mapper để chuyển đổi giữa Entity (ClubApplications) và DTO (ClubApplicationResponse)
     * Giúp tách biệt layer, không expose entity trực tiếp ra controller
     */
    ClubApplicationMapper clubApplicationMapper;
    
    /**
     * Tạo đơn yêu cầu thành lập CLB mới (Student)
     * 
     * Phương thức này cho phép một Student tạo đơn đăng ký thành lập CLB mới.
     * Trước khi tạo, hệ thống sẽ kiểm tra xem user đã là thành viên active của CLB nào khác chưa.
     * 
     * Quy trình:
     * 1. Lấy thông tin user hiện tại từ Security Context (JWT token)
     * 2. Kiểm tra user chưa là thành viên active của CLB nào
     * 3. Tạo đơn mới với status = DangCho (Đang chờ duyệt)
     * 4. Lưu vào database và trả về response
     * 
     * @param request - DTO chứa thông tin đơn đăng ký (tên CLB, danh mục, mô tả, địa điểm, email, phí membership...)
     * @return ClubApplicationResponse - Thông tin đơn đã được tạo (bao gồm requestId, status...)
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user
     * @throws AppException với ErrorCode.ALREADY_CLUB_MEMBER nếu user đã là thành viên active của CLB khác
     * 
     * @Transactional - Đảm bảo toàn bộ operations trong method này được thực hiện trong một transaction
     *                  Nếu có lỗi xảy ra, tất cả thay đổi sẽ được rollback
     */
    @Transactional
    public ClubApplicationResponse createClubApplication(ClubApplicationRequest request) {
        // Lấy thông tin user hiện tại từ Security Context (Spring Security)
        // SecurityContextHolder chứa thông tin authentication của request hiện tại
        // getAuthentication().getName() trả về subject của JWT token, trong trường hợp này là email của user
        // Đây là cách an toàn để xác định user đang thực hiện request (không thể giả mạo)
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Users creator = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        // KIỂM TRA BUSINESS RULE: User không được là thành viên active của CLB nào khác
        // Lý do: Một user chỉ có thể là thành viên của 1 CLB tại một thời điểm
        // Kiểm tra dựa trên bảng Registers (không dựa vào Role trong Users vì role có thể thay đổi)
        // Điều kiện để được coi là "active member":
        //   - status = DaDuyet (đã được duyệt tham gia CLB)
        //   - isPaid = true (đã thanh toán phí membership)
        List<Registers> activeRegistrations = registerRepository.findByUserAndStatusAndIsPaid(
                creator, JoinStatus.DaDuyet, true);
        
        // Nếu user đã là thành viên active của ít nhất 1 CLB, không cho phép tạo đơn mới
        if (!activeRegistrations.isEmpty()) {
            // Ghi log cảnh báo để theo dõi và debug
            log.warn("User {} is already an active member of {} club(s). Cannot create new club application.", 
                    creator.getEmail(), activeRegistrations.size());
            // Throw exception với error code phù hợp
            throw new AppException(ErrorCode.ALREADY_CLUB_MEMBER);
        }
        
        // Tạo đơn mới với thông tin từ request
        // Sử dụng Builder pattern (Lombok @Builder) để tạo object một cách rõ ràng và dễ đọc
        ClubApplications application = ClubApplications.builder()
                .creator(creator)                              // User tạo đơn (founder tương lai)
                .proposedName(request.getProposedName())       // Tên đề xuất cho CLB
                .category(request.getCategory())               // Danh mục CLB (Học thuật, Thể thao, Nghệ thuật...)
                .purpose(request.getPurpose())                 // Mục đích thành lập CLB
                .description(request.getDescription())         // Mô tả chi tiết về CLB
                .location(request.getLocation())               // Địa điểm hoạt động
                .email(request.getEmail())                     // Email liên hệ của CLB
                .defaultMembershipFee(request.getDefaultMembershipFee())  // Phí membership mặc định (sẽ dùng để tạo gói membership đầu tiên)
                .status(RequestStatus.DangCho)                 // Trạng thái ban đầu: Đang chờ Admin duyệt
                .createdAt(DateTimeUtils.nowVietnam())         // Thời gian tạo (sử dụng timezone Việt Nam)
                .build();
        
        // Lưu đơn vào database
        // JPA sẽ tự động generate requestId (primary key) sau khi save
        application = clubApplicationRepository.save(application);
        
        // Ghi log thông tin để theo dõi và audit
        log.info("Created club application: {} by user: {}", application.getRequestId(), creator.getEmail());
        
        // Chuyển đổi Entity sang DTO Response để trả về client
        // Mapper sẽ map các field cần thiết, có thể tính toán thêm các field derived
        return clubApplicationMapper.toResponse(application);
    }
    
    /**
     * Xem danh sách các đơn yêu cầu mở CLB (Admin) - Có thể filter theo status
     * 
     * Phương thức này cho phép Admin xem tất cả các đơn đăng ký thành lập CLB trong hệ thống.
     * Có thể lọc theo status nếu cần (ví dụ: chỉ xem các đơn đang chờ duyệt).
     * 
     * Kịch bản sử dụng:
     * - Admin xem tất cả đơn: gọi với status = null
     * - Admin xem chỉ đơn đang chờ: gọi với status = DangCho
     * - Admin xem đơn đã duyệt: gọi với status = ChapThuan
     * - Admin xem đơn đã từ chối: gọi với status = TuChoi
     * 
     * @param status - Trạng thái để lọc (DangCho, ChapThuan, TuChoi). 
     *                Nếu null, trả về tất cả đơn (sắp xếp theo thời gian tạo mới nhất trước)
     * @return List<ClubApplicationResponse> - Danh sách đơn đã được map sang DTO
     * 
     * Lưu ý: Không có @Transactional vì đây là read-only operation.
     *        Tuy nhiên, trong trường hợp có nhiều read operations phức tạp,
     *        có thể thêm @Transactional(readOnly = true) để tối ưu performance.
     */
    public List<ClubApplicationResponse> getAllApplications(RequestStatus status) {
        List<ClubApplications> applications;
        
        // Nếu có filter theo status, chỉ lấy các đơn có status đó
        if (status != null) {
            applications = clubApplicationRepository.findByStatus(status);
        } else {
            // Nếu không có filter, lấy tất cả đơn và sắp xếp theo thời gian tạo (mới nhất trước)
            // Thứ tự này giúp Admin dễ dàng xem các đơn mới nhất trước
            applications = clubApplicationRepository.findAllByOrderByCreatedAtDesc();
        }
        
        // Chuyển đổi từ List<ClubApplications> sang List<ClubApplicationResponse>
        // Sử dụng Java 8 Stream API để map từng entity sang DTO
        // Method reference (::) là cách viết ngắn gọn của lambda expression
        return applications.stream()
                .map(clubApplicationMapper::toResponse)  // Map từng entity sang response DTO
                .collect(Collectors.toList());            // Thu thập kết quả vào List
    }
    
    /**
     * Xem lịch sử các đơn mình đã gửi (Student)
     * 
     * Phương thức này cho phép Student xem tất cả các đơn đăng ký thành lập CLB 
     * mà mình đã tạo, bao gồm cả đơn đang chờ duyệt, đã được duyệt, và đã bị từ chối.
     * 
     * Kịch bản sử dụng:
     * - Student muốn kiểm tra trạng thái đơn mình đã gửi
     * - Student muốn xem lại các đơn đã bị từ chối để cải thiện và gửi lại
     * - Student muốn xem lịch sử các CLB mình đã thành lập thành công
     * 
     * @return List<ClubApplicationResponse> - Danh sách tất cả đơn của user hiện tại, 
     *                                         sắp xếp theo thời gian tạo (mới nhất trước)
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user
     * 
     * Lưu ý: Chỉ trả về đơn của chính user đang đăng nhập (xác định từ JWT token)
     */
    public List<ClubApplicationResponse> getMyApplications() {
        // Lấy email của user hiện tại từ Security Context (JWT token)
        // Đảm bảo user chỉ có thể xem đơn của chính mình, không thể xem đơn của user khác
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Users creator = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        // Tìm tất cả đơn mà user này là creator, sắp xếp theo thời gian tạo (mới nhất trước)
        List<ClubApplications> applications = clubApplicationRepository.findByCreatorOrderByCreatedAtDesc(creator);
        
        // Chuyển đổi từ Entity sang DTO Response
        return applications.stream()
                .map(clubApplicationMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Duyệt hoặc từ chối đơn đăng ký thành lập CLB (Admin only)
     * 
     * Đây là phương thức quan trọng nhất trong service này, chịu trách nhiệm:
     * 1. Admin review đơn (duyệt hoặc từ chối)
     * 2. Khi duyệt (ChapThuan), hệ thống tự động thực hiện các bước sau:
     *    - Tạo CLB mới với thông tin từ đơn
     *    - Tạo gói membership mặc định cho CLB
     *    - Thêm founder vào CLB với role ChuTich (Chủ tịch)
     *    - Cập nhật role của founder trong bảng Users
     * 
     * Quy trình chi tiết khi duyệt đơn:
     * Bước 1: Tạo CLB mới trong bảng Clubs
     * Bước 2: Tạo gói membership mặc định (ví dụ: "Thành Viên Cơ Bản") với giá từ đơn
     * Bước 3: Tạo bản ghi trong bảng Registers để founder tham gia CLB với:
     *         - Role: ChuTich (Chủ tịch)
     *         - Status: DaDuyet (đã duyệt)
     *         - isPaid: true (miễn phí cho founder)
     *         - Membership: 10 năm (vĩnh viễn)
     * Bước 4: Cập nhật role của founder trong bảng Users thành ChuTich
     * 
     * @param requestId - ID của đơn cần review
     * @param request - DTO chứa quyết định review (status: ChapThuan/TuChoi, adminNote)
     * @return ClubApplicationResponse - Thông tin đơn sau khi được review
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy reviewer
     * @throws AppException với ErrorCode.APPLICATION_NOT_FOUND nếu không tìm thấy đơn
     * @throws AppException với ErrorCode.APPLICATION_ALREADY_REVIEWED nếu đơn đã được review
     * @throws AppException với ErrorCode.INVALID_APPLICATION_STATUS nếu status không hợp lệ
     * 
     * @Transactional - Rất quan trọng: Toàn bộ operations (tạo CLB, membership, register, update user)
     *                  phải được thực hiện trong một transaction. Nếu có lỗi ở bất kỳ bước nào,
     *                  tất cả sẽ được rollback để đảm bảo data consistency.
     */
    @Transactional
    public ClubApplicationResponse reviewApplication(Integer requestId, ReviewApplicationRequest request) {
        // Lấy thông tin Admin đang thực hiện review từ Security Context
        // Admin này sẽ được lưu vào trường reviewer của đơn để audit trail (biết ai đã duyệt/từ chối)
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Users reviewer = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        // Tìm đơn cần review trong database
        // Nếu không tìm thấy, throw exception với error code phù hợp
        ClubApplications application = clubApplicationRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.APPLICATION_NOT_FOUND));
        
        // Kiểm tra business rule: Đơn phải đang ở trạng thái DangCho (Đang chờ) mới có thể review
        // Nếu đơn đã được review (ChapThuan hoặc TuChoi), không cho phép review lại
        // Điều này đảm bảo một đơn chỉ được xử lý một lần
        if (application.getStatus() != RequestStatus.DangCho) {
            throw new AppException(ErrorCode.APPLICATION_ALREADY_REVIEWED);
        }
        
        // Validate input: Status chỉ được phép là ChapThuan (Duyệt) hoặc TuChoi (Từ chối)
        // Không cho phép các giá trị khác để tránh lỗi logic
        if (request.getStatus() != RequestStatus.ChapThuan && request.getStatus() != RequestStatus.TuChoi) {
            throw new AppException(ErrorCode.INVALID_APPLICATION_STATUS);
        }
        
        // Cập nhật thông tin review vào đơn
        application.setStatus(request.getStatus());                    // Trạng thái mới: ChapThuan hoặc TuChoi
        application.setAdminNote(request.getAdminNote());              // Ghi chú của admin (có thể giải thích lý do duyệt/từ chối)
        application.setReviewer(reviewer);                             // Admin đã thực hiện review (để audit)
        application.setUpdatedAt(DateTimeUtils.nowVietnam());          // Thời gian cập nhật (timezone Việt Nam)
        
        // NẾU DUYỆT ĐƠN (ChapThuan): Tự động tạo CLB mới và setup toàn bộ ban đầu
        // Đây là phần quan trọng nhất: Khi đơn được duyệt, hệ thống phải tự động khởi tạo
        // một CLB hoàn chỉnh, không cần Admin phải làm thủ công
        if (request.getStatus() == RequestStatus.ChapThuan) {
            // ========== BƯỚC 1: TẠO CLB MỚI ==========
            // Tạo entity Clubs với thông tin từ đơn đã được duyệt
            // Thông tin này được copy từ ClubApplications sang Clubs
            Clubs newClub = Clubs.builder()
                    .clubName(application.getProposedName())        // Tên CLB: lấy từ tên đề xuất trong đơn
                    .category(application.getCategory())            // Danh mục: Học thuật, Thể thao, Nghệ thuật...
                    .description(application.getDescription())      // Mô tả chi tiết về CLB
                    .location(application.getLocation())            // Địa điểm hoạt động (phòng, tòa nhà...)
                    .email(application.getEmail())                  // Email liên hệ của CLB
                    .founder(application.getCreator())              // User sáng lập CLB (sẽ trở thành Chủ tịch)
                    .isActive(true)                                 // CLB mới được tạo sẽ active ngay
                    .build();
            
            // Lưu CLB vào database
            // JPA sẽ tự động generate clubId (primary key) sau khi save
            newClub = clubRepository.save(newClub);
            
            // Link đơn với CLB vừa tạo (để có thể trace ngược lại từ CLB về đơn gốc)
            application.setClub(newClub);
            
            // Ghi log để tracking và debugging
            log.info("Club created: {} from application: {}", newClub.getClubId(), requestId);
            
            // ========== BƯỚC 2: TẠO GÓI MEMBERSHIP MẶC ĐỊNH ==========
            // Mỗi CLB cần có ít nhất một gói membership để user có thể tham gia
            // Gói mặc định này được tạo tự động khi CLB được phê duyệt
            // Giá của gói lấy từ defaultMembershipFee trong đơn đăng ký
            Memberships defaultPackage = Memberships.builder()
                    .club(newClub)                                                                  // Gói thuộc về CLB vừa tạo
                    .packageName("Thành Viên Cơ Bản")                                               // Tên gói mặc định
                    .term("1 tháng")                                                                 // Thời hạn membership (có thể extend sau)
                    .price(application.getDefaultMembershipFee())                                   // Giá: lấy từ đơn đăng ký (người tạo đơn đề xuất)
                    .description("Gói thành viên mặc định được tạo tự động khi CLB được phê duyệt")  // Mô tả gói
                    .isActive(true)                                                                  // Gói active ngay
                    .build();
            
            // Lưu gói membership vào database
            // JPA sẽ tự động generate membershipId (primary key)
            defaultPackage = membershipRepository.save(defaultPackage);
            
            // Ghi log để tracking
            log.info("Default membership package created for club: {} with price: {}", 
                    newClub.getClubId(), application.getDefaultMembershipFee());
            
            // ========== BƯỚC 3: THÊM FOUNDER VÀO CLB VỚI ROLE CHỦ TỊCH ==========
            // Founder (người tạo đơn) tự động trở thành thành viên của CLB với role cao nhất: ChuTich (Chủ tịch)
            // Đây là quyền lợi đặc biệt của founder: được làm chủ tịch và miễn phí membership vĩnh viễn
            
            LocalDateTime now = DateTimeUtils.nowVietnam();  // Lấy thời gian hiện tại (timezone Việt Nam)
            
            // Tạo bản ghi đăng ký (Registers) cho founder
            // Registers là bảng quan hệ giữa Users và Clubs, chứa thông tin membership của user trong CLB
            Registers founderRegistration = Registers.builder()
                    .user(application.getCreator())                          // Founder (người tạo đơn)
                    .membershipPackage(defaultPackage)                       // Gói membership: sử dụng gói mặc định vừa tạo
                    .status(JoinStatus.DaDuyet)                              // Trạng thái: Đã duyệt (tự động, không cần chờ)
                    .clubRole(ClubRoleType.ChuTich)                          // Role trong CLB: Chủ tịch (cao nhất)
                    .approver(reviewer)                                      // Người duyệt: Admin đã duyệt đơn
                    .isPaid(true)                                            // Đã thanh toán: true (miễn phí cho founder)
                    .paymentMethod("MIỄN PHÍ - NGƯỜI SÁNG LẬP")              // Phương thức thanh toán: miễn phí (ghi chú)
                    .paymentDate(now)                                        // Ngày thanh toán: ngay bây giờ
                    .startDate(now)                                          // Ngày bắt đầu membership: ngay bây giờ
                    .endDate(now.plusYears(10))                              // Ngày kết thúc: 10 năm sau (coi như vĩnh viễn)
                    .joinDate(now)                                           // Ngày tham gia CLB: ngay bây giờ
                    .createdAt(now)                                          // Thời gian tạo bản ghi
                    .build();
            
            // Đảm bảo clubRole được set đúng trước khi save
            // Có thể có vấn đề với builder pattern trong một số trường hợp, nên set lại để chắc chắn
            founderRegistration.setClubRole(ClubRoleType.ChuTich);
            
            // Lưu vào database
            // JPA sẽ tự động generate subscriptionId (primary key)
            founderRegistration = registerRepository.save(founderRegistration);
            
            // Verify sau khi save để đảm bảo dữ liệu đã được lưu đúng
            // Đây là một safeguard để phát hiện lỗi sớm nếu có vấn đề với JPA/Hibernate
            if (founderRegistration.getClubRole() != ClubRoleType.ChuTich) {
                log.error("ERROR: Founder clubRole was not saved correctly! Expected: ChuTich, Actual: {}", 
                        founderRegistration.getClubRole());
                // Nếu không đúng, set lại và save lại (fix attempt)
                founderRegistration.setClubRole(ClubRoleType.ChuTich);
                founderRegistration = registerRepository.save(founderRegistration);
            }
            
            // Ghi log chi tiết để tracking và debugging
            log.info("Founder {} automatically added as ChuTich of club {} with subscriptionId: {} and clubRole: {}", 
                    application.getCreator().getEmail(), newClub.getClubId(), 
                    founderRegistration.getSubscriptionId(), founderRegistration.getClubRole());
            
            // ========== BƯỚC 4: CẬP NHẬT ROLE CỦA FOUNDER TRONG BẢNG USERS ==========
            // Bảng Users có trường role để xác định role tổng thể của user trong hệ thống
            // Khi founder thành lập CLB, role của họ thay đổi từ SinhVien thành ChuTich
            // Role này ảnh hưởng đến quyền hạn của user trong toàn hệ thống (ví dụ: có thể quản lý CLB)
            
            // Lấy lại user từ database để đảm bảo có entity mới nhất (fresh from DB)
            // Tránh vấn đề với entity đã bị detach hoặc stale
            Users founder = userRepository.findById(application.getCreator().getUserId())
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            
            // Tìm role ChuTich trong bảng Roles
            // Role này phải đã được khởi tạo trong ApplicationInitConfig khi ứng dụng start
            var chuTichRole = roleRepository.findByRoleName(RoleType.ChuTich)
                    .orElseThrow(() -> {
                        // Nếu không tìm thấy role ChuTich, đây là lỗi nghiêm trọng (thiếu data initialization)
                        log.error("ChuTich role not found in database. Please check ApplicationInitConfig.");
                        return new RuntimeException("ChuTich role not found in database");
                    });
            
            // Cập nhật role của founder
            founder.setRole(chuTichRole);
            founder = userRepository.save(founder);
            
            // Flush ngay lập tức để đảm bảo dữ liệu được ghi vào database
            // Điều này quan trọng vì có thể có các operations khác phụ thuộc vào role mới này
            userRepository.flush();
            
            // Ghi log để tracking
            log.info("Founder {} role updated to ChuTich. Role ID: {}, Role Name: {}", 
                    founder.getEmail(), founder.getRole().getRoleId(), founder.getRole().getRoleName());
        }
        
        // Lưu lại đơn với thông tin review đã được cập nhật
        // Nếu đơn bị từ chối, chỉ cần cập nhật status và adminNote
        // Nếu đơn được duyệt, đã được cập nhật thêm trường club ở trên
        application = clubApplicationRepository.save(application);
        
        // Ghi log để audit trail (theo dõi ai đã review đơn nào, khi nào)
        log.info("Application {} reviewed by {}: {}", requestId, reviewer.getEmail(), request.getStatus());
        
        // Trả về response với thông tin đơn sau khi review
        return clubApplicationMapper.toResponse(application);
    }
}
