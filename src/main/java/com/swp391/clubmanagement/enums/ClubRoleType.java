package com.swp391.clubmanagement.enums;

/**
 * ClubRoleType Enum - Định nghĩa các vai trò của thành viên trong CLB
 * 
 * Enum này mô tả các chức vụ/vai trò mà một thành viên có thể có trong một CLB cụ thể:
 * từ thành viên thường đến các vị trí lãnh đạo.
 * 
 * Khác với RoleType (vai trò trong hệ thống), ClubRoleType là vai trò trong từng CLB cụ thể.
 * Một User có thể có RoleType là SinhVien nhưng ClubRoleType là ChuTich trong một CLB.
 * 
 * Sử dụng trong entity Registers để xác định vai trò của user trong CLB.
 */
public enum ClubRoleType {
    /** ThanhVien: Thành viên thường của CLB, không có chức vụ đặc biệt */
    ThanhVien,
    
    /** ChuTich: Chủ tịch CLB - Lãnh đạo cao nhất, có quyền quản lý toàn bộ CLB */
    ChuTich,
    
    /** PhoChuTich: Phó chủ tịch CLB - Lãnh đạo phó, hỗ trợ chủ tịch quản lý CLB */
    PhoChuTich,
    
    /** ThuKy: Thư ký / Thủ quỹ - Quản lý hồ sơ, tài chính, sự kiện của CLB */
    ThuKy
}

