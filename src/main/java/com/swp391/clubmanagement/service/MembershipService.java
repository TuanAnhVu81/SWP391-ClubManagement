// Package định nghĩa service layer - xử lý business logic cho quản lý gói membership
package com.swp391.clubmanagement.service;

// ========== DTO ==========
import com.swp391.clubmanagement.dto.request.MembershipCreateRequest; // Request tạo gói membership
import com.swp391.clubmanagement.dto.request.MembershipUpdateRequest; // Request cập nhật gói membership
import com.swp391.clubmanagement.dto.response.MembershipResponse; // Response thông tin gói membership

// ========== Entity ==========
import com.swp391.clubmanagement.entity.Clubs; // Entity CLB
import com.swp391.clubmanagement.entity.Memberships; // Entity gói membership
import com.swp391.clubmanagement.entity.Users; // Entity người dùng

// ========== Enum ==========
import com.swp391.clubmanagement.enums.ClubRoleType; // Vai trò trong CLB
import com.swp391.clubmanagement.enums.JoinStatus; // Trạng thái tham gia

// ========== Exception ==========
import com.swp391.clubmanagement.exception.AppException; // Custom exception
import com.swp391.clubmanagement.exception.ErrorCode; // Mã lỗi hệ thống

// ========== Mapper ==========
import com.swp391.clubmanagement.mapper.MembershipMapper; // Chuyển đổi Entity <-> DTO

// ========== Repository ==========
import com.swp391.clubmanagement.repository.ClubRepository; // Repository cho bảng Clubs
import com.swp391.clubmanagement.repository.MembershipRepository; // Repository cho bảng Memberships
import com.swp391.clubmanagement.repository.RegisterRepository; // Repository cho bảng Registers
import com.swp391.clubmanagement.repository.UserRepository; // Repository cho bảng Users

// ========== Lombok ==========
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor; // Tự động tạo constructor inject dependencies
import lombok.experimental.FieldDefaults; // Tự động thêm private final cho fields
import lombok.extern.slf4j.Slf4j; // Tự động tạo logger

// ========== Spring Framework ==========
import org.springframework.security.core.context.SecurityContextHolder; // Lấy user hiện tại từ JWT
import org.springframework.stereotype.Service; // Đánh dấu class là Spring Service Bean
import org.springframework.transaction.annotation.Transactional; // Quản lý transaction

// ========== Java Standard Library ==========
import java.util.Arrays; // Mảng
import java.util.List; // Danh sách

/**
 * Service quản lý gói membership (gói thành viên) của CLB
 * 
 * Chức năng chính:
 * - Xem danh sách gói membership của CLB (Public)
 * - Xem chi tiết gói membership (Public)
 * - Tạo gói membership mới (Leader only)
 * - Cập nhật thông tin gói (Leader only)
 * - Đóng gói membership (Leader only, soft delete)
 * 
 * Business Rules:
 * - Chỉ Leader (ChuTich, PhoChuTich) hoặc Founder mới được quản lý gói membership
 * - Đóng gói là soft delete (set isActive = false), không xóa khỏi database
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
public class MembershipService {
    /** Repository thao tác với bảng memberships */
    MembershipRepository membershipRepository;
    
    /** Repository thao tác với bảng clubs */
    ClubRepository clubRepository;
    
    /** Repository thao tác với bảng registers (để kiểm tra quyền Leader) */
    RegisterRepository registerRepository;
    
    /** Repository thao tác với bảng users */
    UserRepository userRepository;
    
    /** Mapper chuyển đổi Entity (Memberships) <-> DTO (MembershipResponse) */
    MembershipMapper membershipMapper;

    /**
     * Lấy danh sách các gói thành viên active của 1 CLB
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
     * Lấy chi tiết 1 gói
     */
    public MembershipResponse getPackageById(Integer packageId) {
        Memberships membership = membershipRepository.findById(packageId)
                .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_NOT_FOUND));
        return membershipMapper.toMembershipResponse(membership);
    }
    
    /**
     * Tạo gói thành viên mới (Leader only)
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
     * Cập nhật thông tin gói (Tên, giá, mô tả) - Leader only
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
     * Đóng gói đăng ký (Soft delete: set is_active=false) - Leader only
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

