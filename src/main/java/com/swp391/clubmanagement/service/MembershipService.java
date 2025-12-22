package com.swp391.clubmanagement.service;

import com.swp391.clubmanagement.dto.request.MembershipCreateRequest;
import com.swp391.clubmanagement.dto.request.MembershipUpdateRequest;
import com.swp391.clubmanagement.dto.response.MembershipResponse;
import com.swp391.clubmanagement.entity.Clubs;
import com.swp391.clubmanagement.entity.Memberships;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.enums.ClubRoleType;
import com.swp391.clubmanagement.enums.JoinStatus;
import com.swp391.clubmanagement.exception.AppException;
import com.swp391.clubmanagement.exception.ErrorCode;
import com.swp391.clubmanagement.mapper.MembershipMapper;
import com.swp391.clubmanagement.repository.ClubRepository;
import com.swp391.clubmanagement.repository.MembershipRepository;
import com.swp391.clubmanagement.repository.RegisterRepository;
import com.swp391.clubmanagement.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * MembershipService - Service xử lý logic nghiệp vụ cho quản lý gói membership
 * 
 * Service này chịu trách nhiệm quản lý các gói membership (thành viên) của CLB:
 * - Xem danh sách gói membership của CLB (public)
 * - Xem chi tiết 1 gói membership (public)
 * - Tạo gói membership mới (leader only)
 * - Cập nhật thông tin gói (leader only: tên, giá, mô tả)
 * - Đóng gói membership (leader only: soft delete, set isActive = false)
 * 
 * Business rules:
 * - Chỉ Leader (ChuTich, PhoChuTich) hoặc Founder mới được quản lý gói membership
 * - Gói membership phải active (isActive = true) mới hiển thị cho user đăng ký
 * - Soft delete: Khi đóng gói, chỉ set isActive = false, không xóa khỏi database (giữ lịch sử)
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
public class MembershipService {
    /**
     * Repository để truy vấn và thao tác với bảng Memberships trong database
     */
    MembershipRepository membershipRepository;
    
    /**
     * Repository để truy vấn và thao tác với bảng Clubs trong database
     * Dùng để kiểm tra CLB tồn tại và check quyền Leader
     */
    ClubRepository clubRepository;
    
    /**
     * Repository để truy vấn và thao tác với bảng Registers trong database
     * Dùng để check quyền Leader (user có phải Leader của CLB không)
     */
    RegisterRepository registerRepository;
    
    /**
     * Repository để truy vấn và thao tác với bảng Users trong database
     * Dùng để lấy thông tin user hiện tại (leader)
     */
    UserRepository userRepository;
    
    /**
     * Mapper để chuyển đổi giữa Entity (Memberships) và DTO (MembershipResponse)
     */
    MembershipMapper membershipMapper;

    /**
     * Lấy danh sách các gói membership đang active của một CLB (Public)
     * 
     * Phương thức này cho phép user xem tất cả các gói membership đang active của một CLB.
     * User có thể chọn gói phù hợp để đăng ký tham gia CLB.
     * 
     * @param clubId - ID của CLB cần xem danh sách gói membership
     * @return List<MembershipResponse> - Danh sách gói membership đã được map sang DTO
     * @throws AppException với ErrorCode.CLUB_NOT_FOUND nếu không tìm thấy CLB
     * 
     * Lưu ý:
     * - Public endpoint: Không cần authentication, ai cũng có thể xem
     * - Chỉ trả về các gói đang active (isActive = true)
     * - Gói đã đóng (isActive = false) sẽ không hiển thị
     */
    public List<MembershipResponse> getPackagesByClub(Integer clubId) {
        // Kiểm tra CLB có tồn tại không
        if (!clubRepository.existsById(clubId)) {
            throw new AppException(ErrorCode.CLUB_NOT_FOUND);
        }
        
        List<Memberships> packages = membershipRepository.findByClub_ClubIdAndIsActive(clubId, true);
        return membershipMapper.toMembershipResponseList(packages);
    }

    /**
     * Lấy chi tiết một gói membership cụ thể (Public)
     * 
     * Phương thức này cho phép user xem chi tiết thông tin của một gói membership,
     * bao gồm tên, giá, thời hạn, mô tả, và thông tin CLB.
     * 
     * @param packageId - ID của gói membership cần xem chi tiết
     * @return MembershipResponse - Thông tin chi tiết gói membership đã được map sang DTO
     * @throws AppException với ErrorCode.PACKAGE_NOT_FOUND nếu không tìm thấy gói
     * 
     * Lưu ý:
     * - Public endpoint: Không cần authentication, ai cũng có thể xem
     * - Có thể xem cả gói đã đóng (isActive = false) để tham khảo
     */
    public MembershipResponse getPackageById(Integer packageId) {
        Memberships membership = membershipRepository.findById(packageId)
                .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_NOT_FOUND));
        return membershipMapper.toMembershipResponse(membership);
    }
    
    /**
     * Tạo gói membership mới cho CLB (Leader only)
     * 
     * Phương thức này cho phép Leader tạo gói membership mới cho CLB của mình.
     * Sau khi tạo, gói sẽ ở trạng thái active (isActive = true) và user có thể đăng ký ngay.
     * 
     * Business rules:
     * - Chỉ Leader (ChuTich, PhoChuTich) hoặc Founder mới được tạo gói
     * - Gói mới tạo sẽ tự động active (isActive = true)
     * - Mỗi CLB có thể có nhiều gói membership khác nhau (ví dụ: 1 tháng, 3 tháng, 1 năm)
     * 
     * @param clubId - ID của CLB cần tạo gói membership
     * @param request - DTO chứa thông tin gói: packageName, term (thời hạn), price, description
     * @return MembershipResponse - Thông tin gói đã được tạo, đã được map sang DTO
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user hiện tại
     * @throws AppException với ErrorCode.CLUB_NOT_FOUND nếu không tìm thấy CLB
     * @throws AppException với ErrorCode.NOT_CLUB_LEADER nếu user không phải leader/founder
     * 
     * @Transactional - Đảm bảo toàn bộ operations được thực hiện trong một transaction
     */
    @Transactional
    public MembershipResponse createPackage(Integer clubId, MembershipCreateRequest request) {
        // Lấy user hiện tại
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Users currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        // Tìm CLB
        Clubs club = clubRepository.findById(clubId)
                .orElseThrow(() -> new AppException(ErrorCode.CLUB_NOT_FOUND));
        
        // Kiểm tra quyền: Phải là Leader (ChuTich, PhoChuTich) hoặc Founder
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
        
        // Tạo gói mới
        Memberships newPackage = Memberships.builder()
                .club(club)
                .packageName(request.getPackageName())
                .term(request.getTerm())
                .price(request.getPrice())
                .description(request.getDescription())
                .isActive(true)
                .build();
        
        newPackage = membershipRepository.save(newPackage);
        log.info("Package {} created for club {} by user {}", newPackage.getPackageId(), clubId, currentUser.getEmail());
        
        return membershipMapper.toMembershipResponse(newPackage);
    }
    
    /**
     * Cập nhật thông tin gói membership (Leader only)
     * 
     * Phương thức này cho phép Leader cập nhật thông tin của gói membership đã có:
     * - Tên gói (packageName)
     * - Thời hạn (term)
     * - Giá (price)
     * - Mô tả (description)
     * 
     * Business rules:
     * - Chỉ Leader (ChuTich, PhoChuTich) hoặc Founder mới được cập nhật
     * - Leader phải là leader của CLB chứa gói này
     * - Có thể cập nhật gói đang active hoặc đã đóng
     * 
     * Lưu ý quan trọng:
     * - Khi cập nhật giá, các đơn đăng ký đã tạo với giá cũ sẽ không bị ảnh hưởng
     * - Chỉ các đơn đăng ký mới sau khi update sẽ áp dụng giá mới
     * 
     * @param packageId - ID của gói membership cần cập nhật
     * @param request - DTO chứa thông tin cần cập nhật (packageName, term, price, description)
     * @return MembershipResponse - Thông tin gói sau khi được cập nhật, đã được map sang DTO
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user hiện tại
     * @throws AppException với ErrorCode.PACKAGE_NOT_FOUND nếu không tìm thấy gói
     * @throws AppException với ErrorCode.NOT_CLUB_LEADER nếu user không phải leader/founder
     * 
     * @Transactional - Đảm bảo toàn bộ operations được thực hiện trong một transaction
     */
    @Transactional
    public MembershipResponse updatePackage(Integer packageId, MembershipUpdateRequest request) {
        // Lấy user hiện tại
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Users currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        // Tìm package
        Memberships membership = membershipRepository.findById(packageId)
                .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_NOT_FOUND));
        
        Integer clubId = membership.getClub().getClubId();
        
        // Kiểm tra quyền: Phải là Leader (ChuTich hoặc PhoChuTich) của CLB
        boolean isLeader = registerRepository.existsByUserAndMembershipPackage_Club_ClubIdAndClubRoleInAndStatusAndIsPaid(
                currentUser, 
                clubId,
                Arrays.asList(ClubRoleType.ChuTich, ClubRoleType.PhoChuTich),
                JoinStatus.DaDuyet,
                true
        );
        
        if (!isLeader && !membership.getClub().getFounder().getUserId().equals(currentUser.getUserId())) {
            throw new AppException(ErrorCode.NOT_CLUB_LEADER);
        }
        
        // Cập nhật thông tin
        membership.setPackageName(request.getPackageName());
        membership.setTerm(request.getTerm());
        membership.setPrice(request.getPrice());
        if (request.getDescription() != null) {
            membership.setDescription(request.getDescription());
        }
        
        membership = membershipRepository.save(membership);
        log.info("Package {} updated by user {}", packageId, currentUser.getEmail());
        
        return membershipMapper.toMembershipResponse(membership);
    }
    
    /**
     * Đóng gói membership (Soft delete: set isActive=false) - Leader only
     * 
     * Phương thức này cho phép Leader đóng (deactivate) một gói membership.
     * Sau khi đóng, gói sẽ không còn hiển thị cho user đăng ký, nhưng vẫn được giữ lại trong database.
     * 
     * Business rules:
     * - Chỉ Leader (ChuTich, PhoChuTich) hoặc Founder mới được đóng gói
     * - Leader phải là leader của CLB chứa gói này
     * - Soft delete: Chỉ set isActive = false, không xóa khỏi database
     * 
     * Lý do soft delete:
     * - Giữ lại lịch sử: Các đơn đăng ký đã sử dụng gói này vẫn có thể reference
     * - Có thể mở lại gói sau này nếu cần (set isActive = true)
     * - Không ảnh hưởng đến các đơn đăng ký đã tạo với gói này
     * 
     * @param packageId - ID của gói membership cần đóng
     * @throws AppException với ErrorCode.USER_NOT_FOUND nếu không tìm thấy user hiện tại
     * @throws AppException với ErrorCode.PACKAGE_NOT_FOUND nếu không tìm thấy gói
     * @throws AppException với ErrorCode.NOT_CLUB_LEADER nếu user không phải leader/founder
     * 
     * @Transactional - Đảm bảo toàn bộ operations được thực hiện trong một transaction
     */
    @Transactional
    public void deletePackage(Integer packageId) {
        // Lấy user hiện tại
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Users currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        // Tìm package
        Memberships membership = membershipRepository.findById(packageId)
                .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_NOT_FOUND));
        
        Integer clubId = membership.getClub().getClubId();
        
        // Kiểm tra quyền: Phải là Leader của CLB
        boolean isLeader = registerRepository.existsByUserAndMembershipPackage_Club_ClubIdAndClubRoleInAndStatusAndIsPaid(
                currentUser,
                clubId,
                Arrays.asList(ClubRoleType.ChuTich, ClubRoleType.PhoChuTich),
                JoinStatus.DaDuyet,
                true
        );
        
        if (!isLeader && !membership.getClub().getFounder().getUserId().equals(currentUser.getUserId())) {
            throw new AppException(ErrorCode.NOT_CLUB_LEADER);
        }
        
        // Soft delete: set is_active = false
        membership.setIsActive(false);
        membershipRepository.save(membership);
        
        log.info("Package {} deactivated by user {}", packageId, currentUser.getEmail());
    }
}

