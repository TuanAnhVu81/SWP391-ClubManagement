package com.swp391.clubmanagement.configuration;

import com.swp391.clubmanagement.utils.DateTimeUtils;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA Entity Listener để tự động set timestamp theo timezone Việt Nam
 */
public class EntityAuditListener {
    
    @PrePersist
    public void setCreatedAt(Object entity) {
        setFieldValue(entity, "createdAt", DateTimeUtils.nowVietnam());
        setFieldValue(entity, "establishedDate", LocalDate.now(DateTimeUtils.VIETNAM_ZONE));
    }
    
    @PreUpdate
    public void setUpdatedAt(Object entity) {
        setFieldValue(entity, "updatedAt", DateTimeUtils.nowVietnam());
    }
    
    private void setFieldValue(Object entity, String fieldName, Object value) {
        try {
            Field field = findField(entity.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                Object currentValue = field.get(entity);
                // Chỉ set nếu field hiện tại là null
                if (currentValue == null) {
                    field.set(entity, value);
                }
            }
        } catch (Exception e) {
            // Ignore if field doesn't exist or cannot be set
        }
    }
    
    private Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }
}

