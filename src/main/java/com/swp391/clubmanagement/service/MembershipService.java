package com.swp391.clubmanagement.service;

import com.swp391.clubmanagement.dto.request.MembershipUpdateRequest;
import com.swp391.clubmanagement.dto.response.MembershipResponse;
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

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class MembershipService {
    MembershipRepository membershipRepository;
    ClubRepository clubRepository;
    RegisterRepository registerRepository;
    UserRepository userRepository;
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

