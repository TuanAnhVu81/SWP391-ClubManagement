package com.swp391.clubmanagement.enums;

/**
 * RoleType Enum - Định nghĩa các vai trò (Role) trong hệ thống
 * 
 * Enum này mô tả các loại vai trò mà một User có thể có trong hệ thống quản lý CLB.
 * Vai trò quyết định quyền truy cập và chức năng mà User có thể sử dụng:
 * - QuanTriVien: Có quyền cao nhất, quản lý toàn hệ thống, duyệt đơn thành lập CLB
 * - SinhVien: User bình thường, có thể đăng ký tham gia CLB
 * - ChuTich: Người đứng đầu CLB, có quyền quản lý CLB và duyệt đơn đăng ký
 */
public enum RoleType {
    /** Quản trị viên hệ thống (School Admin): Có quyền quản lý toàn bộ hệ thống, duyệt đơn thành lập CLB */
    QuanTriVien,
    
    /** Sinh viên (User): User bình thường, có thể đăng ký tham gia các CLB */
    SinhVien,
    
    /** Chủ tịch CLB (Club President/Founder): Người sáng lập hoặc đứng đầu một CLB, có quyền quản lý CLB đó */
    ChuTich
}

