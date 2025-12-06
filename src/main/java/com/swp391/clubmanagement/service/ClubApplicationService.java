package com.swp391.clubmanagement.service;

import com.swp391.clubmanagement.dto.request.ClubApplicationRequest;
import com.swp391.clubmanagement.dto.request.ReviewApplicationRequest;
import com.swp391.clubmanagement.dto.response.ClubApplicationResponse;
import com.swp391.clubmanagement.entity.ClubApplications;
import com.swp391.clubmanagement.entity.Clubs;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.enums.RequestStatus;
import com.swp391.clubmanagement.exception.AppException;
import com.swp391.clubmanagement.exception.ErrorCode;
import com.swp391.clubmanagement.mapper.ClubApplicationMapper;
import com.swp391.clubmanagement.repository.ClubApplicationRepository;
import com.swp391.clubmanagement.repository.ClubRepository;
import com.swp391.clubmanagement.repository.UserRepository;
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

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ClubApplicationService {
    
    ClubApplicationRepository clubApplicationRepository;
    ClubRepository clubRepository;
    UserRepository userRepository;
    ClubApplicationMapper clubApplicationMapper;
    
    /**
     * Tạo đơn yêu cầu thành lập CLB mới (Student)
     */
    @Transactional
    public ClubApplicationResponse createClubApplication(ClubApplicationRequest request) {
        // Lấy thông tin user hiện tại từ Security Context
        String studentCode = SecurityContextHolder.getContext().getAuthentication().getName();
        Users creator = userRepository.findByStudentCode(studentCode)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        // Tạo đơn mới
        ClubApplications application = ClubApplications.builder()
                .creator(creator)
                .proposedName(request.getProposedName())
                .category(request.getCategory())
                .purpose(request.getPurpose())
                .status(RequestStatus.DangCho)
                .createdAt(LocalDateTime.now())
                .build();
        
        application = clubApplicationRepository.save(application);
        log.info("Created club application: {} by user: {}", application.getRequestId(), creator.getStudentCode());
        
        return clubApplicationMapper.toResponse(application);
    }
    
    /**
     * Xem danh sách các đơn yêu cầu mở CLB (Admin) - Có thể filter theo status
     */
    public List<ClubApplicationResponse> getAllApplications(RequestStatus status) {
        List<ClubApplications> applications;
        
        if (status != null) {
            applications = clubApplicationRepository.findByStatus(status);
        } else {
            applications = clubApplicationRepository.findAllByOrderByCreatedAtDesc();
        }
        
        return applications.stream()
                .map(clubApplicationMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Xem lịch sử các đơn mình đã gửi (Student)
     */
    public List<ClubApplicationResponse> getMyApplications() {
        String studentCode = SecurityContextHolder.getContext().getAuthentication().getName();
        Users creator = userRepository.findByStudentCode(studentCode)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        List<ClubApplications> applications = clubApplicationRepository.findByCreatorOrderByCreatedAtDesc(creator);
        
        return applications.stream()
                .map(clubApplicationMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Duyệt hoặc từ chối đơn (Admin)
     * Khi duyệt (ChapThuan), hệ thống tự động tạo CLB mới
     */
    @Transactional
    public ClubApplicationResponse reviewApplication(Integer requestId, ReviewApplicationRequest request) {
        // Lấy thông tin admin reviewer
        String studentCode = SecurityContextHolder.getContext().getAuthentication().getName();
        Users reviewer = userRepository.findByStudentCode(studentCode)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        // Tìm đơn
        ClubApplications application = clubApplicationRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.APPLICATION_NOT_FOUND));
        
        // Kiểm tra đơn đã được review chưa
        if (application.getStatus() != RequestStatus.DangCho) {
            throw new AppException(ErrorCode.APPLICATION_ALREADY_REVIEWED);
        }
        
        // Validate status (chỉ cho phép ChapThuan hoặc TuChoi)
        if (request.getStatus() != RequestStatus.ChapThuan && request.getStatus() != RequestStatus.TuChoi) {
            throw new AppException(ErrorCode.INVALID_APPLICATION_STATUS);
        }
        
        // Cập nhật thông tin review
        application.setStatus(request.getStatus());
        application.setAdminNote(request.getAdminNote());
        application.setReviewer(reviewer);
        application.setUpdatedAt(LocalDateTime.now());
        
        // Nếu duyệt, tự động tạo CLB mới
        if (request.getStatus() == RequestStatus.ChapThuan) {
            Clubs newClub = Clubs.builder()
                    .clubName(application.getProposedName())
                    .category(application.getCategory())
                    .description(application.getPurpose())
                    .founder(application.getCreator())
                    .isActive(true)
                    .build();
            
            newClub = clubRepository.save(newClub);
            application.setClub(newClub);
            
            log.info("Club created: {} from application: {}", newClub.getClubId(), requestId);
        }
        
        application = clubApplicationRepository.save(application);
        log.info("Application {} reviewed by {}: {}", requestId, reviewer.getStudentCode(), request.getStatus());
        
        return clubApplicationMapper.toResponse(application);
    }
}
