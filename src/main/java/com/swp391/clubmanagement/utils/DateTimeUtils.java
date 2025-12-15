package com.swp391.clubmanagement.utils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Utility class để xử lý thời gian theo timezone Việt Nam (UTC+7)
 */
public class DateTimeUtils {
    
    /**
     * Timezone Việt Nam: Asia/Ho_Chi_Minh (UTC+7)
     */
    public static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    
    /**
     * Lấy thời gian hiện tại theo giờ Việt Nam
     * @return LocalDateTime theo timezone Việt Nam
     */
    public static LocalDateTime nowVietnam() {
        return ZonedDateTime.now(VIETNAM_ZONE).toLocalDateTime();
    }
    
    /**
     * Chuyển đổi LocalDateTime sang ZonedDateTime với timezone Việt Nam
     * @param localDateTime LocalDateTime cần chuyển đổi
     * @return ZonedDateTime với timezone Việt Nam
     */
    public static ZonedDateTime toVietnamZone(LocalDateTime localDateTime) {
        return localDateTime.atZone(VIETNAM_ZONE);
    }
    
    /**
     * Lấy thời gian hiện tại dạng ZonedDateTime với timezone Việt Nam
     * @return ZonedDateTime với timezone Việt Nam
     */
    public static ZonedDateTime nowVietnamZoned() {
        return ZonedDateTime.now(VIETNAM_ZONE);
    }
}

