package com.swp391.clubmanagement.service;

import com.swp391.clubmanagement.dto.request.ClubUpdateRequest;
import com.swp391.clubmanagement.dto.response.ClubMemberResponse;
import com.swp391.clubmanagement.dto.response.ClubResponse;
import com.swp391.clubmanagement.entity.Clubs;
import com.swp391.clubmanagement.entity.Registers;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.enums.ClubCategory;
import com.swp391.clubmanagement.enums.JoinStatus;
import com.swp391.clubmanagement.exception.AppException;
import com.swp391.clubmanagement.exception.ErrorCode;
import com.swp391.clubmanagement.mapper.ClubMapper;
import com.swp391.clubmanagement.repository.ClubRepository;
import com.swp391.clubmanagement.repository.RegisterRepository;
import com.swp391.clubmanagement.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ClubService {
    
    ClubRepository clubRepository;
    RegisterRepository registerRepository;
    UserRepository userRepository;
    ClubMapper clubMapper;
    
    /**
     * Lấy danh sách tất cả CLB đang hoạt động (Public)
     * Có thể search theo tên và filter theo category
     */
    public List<ClubResponse> getAllClubs(String name, ClubCategory category) {
        List<Clubs> clubs;
        
        if (name != null || category != null) {
            clubs = clubRepository.searchByNameAndCategory(name, category);
        } else {
            clubs = clubRepository.findByIsActiveTrue();
        }
        
        return clubs.stream()
                .map(clubMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Xem chi tiết thông tin 1 CLB (Public)
     */
    public ClubResponse getClubById(Integer clubId) {
        Clubs club = clubRepository.findById(clubId)
                .orElseThrow(() -> new AppException(ErrorCode.CLUB_NOT_FOUND));
        
        return clubMapper.toResponse(club);
    }
    
    /**
     * Cập nhật thông tin CLB - Logo, mô tả, địa điểm sinh hoạt (Leader only)
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
     * Xem danh sách thành viên của CLB (Public)
     */
    public List<ClubMemberResponse> getClubMembers(Integer clubId) {
        // Kiểm tra CLB có tồn tại không
        if (!clubRepository.existsById(clubId)) {
            throw new AppException(ErrorCode.CLUB_NOT_FOUND);
        }
        
        // Lấy danh sách thành viên đã được duyệt
        List<Registers> registers = registerRepository.findByClubIdAndStatus(clubId, JoinStatus.DaDuyet);
        
        return registers.stream()
                .map(clubMapper::toMemberResponse)
                .collect(Collectors.toList());
    }
}
