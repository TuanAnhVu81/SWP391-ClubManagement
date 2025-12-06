package com.swp391.clubmanagement.dto.request;

import com.swp391.clubmanagement.enums.RequestStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReviewApplicationRequest {
    
    @NotNull(message = "Trạng thái duyệt không được để trống")
    RequestStatus status; // ChapThuan hoặc TuChoi
    
    String adminNote; // Ghi chú của admin (optional)
}
