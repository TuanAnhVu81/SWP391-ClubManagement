package com.swp391.clubmanagement.repository;

import com.swp391.clubmanagement.entity.ClubApplications;
import com.swp391.clubmanagement.entity.Clubs;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClubApplicationRepository extends JpaRepository<ClubApplications, Integer> {
    
    // Tìm đơn theo trạng thái
    List<ClubApplications> findByStatus(RequestStatus status);
    
    // Tìm đơn theo người tạo (để xem lịch sử đơn của mình)
    List<ClubApplications> findByCreatorOrderByCreatedAtDesc(Users creator);
    
    // Tìm tất cả đơn, sắp xếp theo ngày tạo mới nhất
    List<ClubApplications> findAllByOrderByCreatedAtDesc();
    
    // Tìm đơn theo club (để xóa khi xóa club)
    List<ClubApplications> findByClub(Clubs club);
}
