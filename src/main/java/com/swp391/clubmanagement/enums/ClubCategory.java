package com.swp391.clubmanagement.enums;

/**
 * ClubCategory Enum - Định nghĩa các danh mục (category) của CLB
 * 
 * Enum này phân loại các CLB theo lĩnh vực hoạt động chính:
 * giúp người dùng dễ dàng tìm kiếm và lọc CLB theo sở thích.
 * 
 * Sử dụng trong entity Clubs và ClubApplications để phân loại CLB.
 */
public enum ClubCategory {
    /** HocThuat: Các CLB học thuật như IT, English, Khoa học, Nghiên cứu... */
    HocThuat,
    
    /** TheThao: Các CLB thể thao như Bóng đá, Cầu lông, Bóng rổ, Bơi lội... */
    TheThao,
    
    /** NgheThuat: Các CLB nghệ thuật như Âm nhạc, Nhảy múa, Hội họa, Nhiếp ảnh... */
    NgheThuat,
    
    /** TinhNguyen: Các CLB tình nguyện, hoạt động xã hội, thiện nguyện... */
    TinhNguyen,
    
    /** Khac: Các CLB không thuộc các danh mục trên hoặc đa lĩnh vực */
    Khac
}

