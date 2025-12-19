package com.swp391.clubmanagement.repository;

import com.swp391.clubmanagement.entity.ClubApplications;
import com.swp391.clubmanagement.entity.Clubs;
import com.swp391.clubmanagement.entity.Users;
import com.swp391.clubmanagement.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ClubApplicationRepository - Spring Data JPA Repository cho entity ClubApplications
 * 
 * Interface này kế thừa JpaRepository, cung cấp sẵn các method CRUD cơ bản.
 * Sử dụng để quản lý các đơn xin thành lập CLB.
 */
@Repository
public interface ClubApplicationRepository extends JpaRepository<ClubApplications, Integer> {
    /**
     * Tìm tất cả đơn xin thành lập CLB theo trạng thái
     * 
     * @param status Trạng thái: DangCho, ChapThuan, TuChoi
     * @return Danh sách đơn có trạng thái tương ứng
     */
    List<ClubApplications> findByStatus(RequestStatus status);
    
    /**
     * Tìm tất cả đơn xin thành lập CLB của một user (để xem lịch sử đơn của mình)
     * Sắp xếp theo ngày tạo mới nhất trước (DESC)
     * 
     * @param creator User tạo đơn
     * @return Danh sách đơn của user, sắp xếp từ mới đến cũ
     */
    List<ClubApplications> findByCreatorOrderByCreatedAtDesc(Users creator);
    
    /**
     * Tìm tất cả đơn xin thành lập CLB, sắp xếp theo ngày tạo mới nhất trước
     * Sử dụng trong Admin Dashboard để xem tất cả đơn mới nhất
     * 
     * @return Danh sách tất cả đơn, sắp xếp từ mới đến cũ
     */
    List<ClubApplications> findAllByOrderByCreatedAtDesc();
    
    /**
     * Tìm đơn xin thành lập CLB theo CLB đã được tạo (dùng khi xóa CLB để xóa luôn đơn liên quan)
     * 
     * @param club CLB đã được tạo từ đơn
     * @return Danh sách đơn liên quan đến CLB (thường chỉ có 1 đơn)
     */
    List<ClubApplications> findByClub(Clubs club);
}
