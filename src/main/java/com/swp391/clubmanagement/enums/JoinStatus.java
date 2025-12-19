package com.swp391.clubmanagement.enums;

/**
 * JoinStatus Enum - Định nghĩa các trạng thái của đơn đăng ký tham gia CLB
 * 
 * Enum này mô tả các trạng thái mà một đơn đăng ký (Register) có thể có trong quy trình:
 * từ khi user đăng ký, qua bước duyệt, đến khi tham gia hoặc bị từ chối.
 * 
 * Sử dụng trong entity Registers để theo dõi trạng thái của từng đơn đăng ký.
 */
public enum JoinStatus {
    /** ChoDuyet (PENDING): User đã đăng ký, đang chờ Leader/Admin duyệt đơn */
    ChoDuyet,
    
    /** DaDuyet (APPROVED): Đơn đã được duyệt, user đã chính thức tham gia CLB */
    DaDuyet,
    
    /** TuChoi (REJECTED): Đơn bị từ chối, hồ sơ không đạt yêu cầu */
    TuChoi,
    
    /** DaRoiCLB (LEFT): User đã tự rời CLB (resign) */
    DaRoiCLB,
    
    /** HetHan (EXPIRED): Gói membership đã hết hạn, user không còn là thành viên */
    HetHan
}

