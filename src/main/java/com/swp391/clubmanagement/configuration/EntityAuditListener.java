package com.swp391.clubmanagement.configuration;

import com.swp391.clubmanagement.utils.DateTimeUtils;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.lang.reflect.Field;
import java.time.LocalDate;

/**
 * EntityAuditListener - JPA Entity Listener để tự động cập nhật timestamp
 * 
 * Listener này được đăng ký với các Entity thông qua annotation @EntityListeners.
 * Tự động set các trường timestamp khi entity được tạo mới (PrePersist) hoặc cập nhật (PreUpdate).
 * 
 * Đặc biệt: Sử dụng timezone Việt Nam (Asia/Ho_Chi_Minh) thay vì timezone mặc định của server.
 * 
 * Các trường được xử lý:
 * - createdAt: Tự động set khi entity được tạo lần đầu (chỉ set nếu null)
 * - updatedAt: Tự động set mỗi khi entity được cập nhật
 * - establishedDate: Tự động set khi entity Clubs được tạo (chỉ set nếu null)
 */
public class EntityAuditListener {
    
    /**
     * PrePersist callback: Được gọi trước khi entity được lưu lần đầu tiên
     * 
     * Tự động set:
     * - createdAt: Thời điểm hiện tại (timezone VN) nếu field null
     * - establishedDate: Ngày hiện tại (timezone VN) nếu field null (cho entity Clubs)
     * 
     * @param entity Entity đang được lưu
     */
    @PrePersist
    public void setCreatedAt(Object entity) {
        // Set createdAt với thời gian hiện tại theo timezone Việt Nam
        setFieldValue(entity, "createdAt", DateTimeUtils.nowVietnam());
        // Set establishedDate cho entity Clubs (nếu có field này)
        setFieldValue(entity, "establishedDate", LocalDate.now(DateTimeUtils.VIETNAM_ZONE));
    }
    
    /**
     * PreUpdate callback: Được gọi trước khi entity được cập nhật
     * 
     * Tự động set:
     * - updatedAt: Thời điểm hiện tại (timezone VN) nếu field null
     * 
     * @param entity Entity đang được cập nhật
     */
    @PreUpdate
    public void setUpdatedAt(Object entity) {
        // Set updatedAt với thời gian hiện tại theo timezone Việt Nam
        setFieldValue(entity, "updatedAt", DateTimeUtils.nowVietnam());
    }
    
    /**
     * Set giá trị cho một field của entity bằng Reflection
     * 
     * Chỉ set giá trị nếu field hiện tại là null (không ghi đè giá trị đã có).
     * Nếu field không tồn tại hoặc không thể set, bỏ qua (không throw exception).
     * 
     * @param entity Entity cần set giá trị
     * @param fieldName Tên field cần set
     * @param value Giá trị muốn set
     */
    private void setFieldValue(Object entity, String fieldName, Object value) {
        try {
            // Tìm field trong class hoặc superclass
            Field field = findField(entity.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);  // Cho phép truy cập private field
                Object currentValue = field.get(entity);
                // Chỉ set nếu field hiện tại là null (không ghi đè giá trị đã có)
                if (currentValue == null) {
                    field.set(entity, value);
                }
            }
        } catch (Exception e) {
            // Bỏ qua nếu field không tồn tại hoặc không thể set (không throw exception)
            // Điều này cho phép listener hoạt động với các entity có cấu trúc khác nhau
        }
    }
    
    /**
     * Tìm field trong class hoặc các superclass bằng Reflection
     * 
     * Tìm kiếm field trong class hiện tại, nếu không tìm thấy thì tìm trong superclass.
     * 
     * @param clazz Class cần tìm field
     * @param fieldName Tên field cần tìm
     * @return Field object nếu tìm thấy, null nếu không tìm thấy
     */
    private Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                // Tìm field trong class hiện tại
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                // Nếu không tìm thấy, tiếp tục tìm trong superclass
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }
}

