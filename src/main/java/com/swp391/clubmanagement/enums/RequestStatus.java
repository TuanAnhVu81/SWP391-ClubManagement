package com.swp391.clubmanagement.enums;

/**
 * RequestStatus Enum - Định nghĩa các trạng thái của đơn xin thành lập CLB
 * 
 * Enum này mô tả các trạng thái mà một đơn xin thành lập CLB (ClubApplication) có thể có:
 * từ khi user gửi đơn, qua bước Admin duyệt, đến khi được chấp thuận hoặc từ chối.
 * 
 * Sử dụng trong entity ClubApplications để theo dõi trạng thái duyệt của từng đơn.
 */
public enum RequestStatus {
    /** DangCho: Đơn đang chờ Admin (QuanTriVien) xem xét và duyệt */
    DangCho,
    
    /** ChapThuan: Đơn đã được Admin chấp thuận, CLB mới sẽ được tạo */
    ChapThuan,
    
    /** TuChoi: Đơn bị Admin từ chối, không cho phép thành lập CLB */
    TuChoi
}

