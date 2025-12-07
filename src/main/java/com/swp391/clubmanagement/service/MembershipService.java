package com.swp391.clubmanagement.service;

import com.swp391.clubmanagement.dto.response.MembershipResponse;
import com.swp391.clubmanagement.entity.Memberships;
import com.swp391.clubmanagement.exception.AppException;
import com.swp391.clubmanagement.exception.ErrorCode;
import com.swp391.clubmanagement.mapper.MembershipMapper;
import com.swp391.clubmanagement.repository.ClubRepository;
import com.swp391.clubmanagement.repository.MembershipRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MembershipService {
    MembershipRepository membershipRepository;
    ClubRepository clubRepository;
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
}

