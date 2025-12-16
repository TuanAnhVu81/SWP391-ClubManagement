package com.swp391.clubmanagement.enums;

public enum JoinStatus {
    ChoDuyet,    // PENDING: Đã đăng ký, chờ Leader duyệt
    DaDuyet,     // APPROVED: Đã vào CLB
    TuChoi,      // REJECTED: Hồ sơ không đạt
    DaRoiCLB,    // LEFT: Đã tự rời CLB
    HetHan       // EXPIRED: Gói membership đã hết hạn
}

