package com.swp391.clubmanagement.entity;

import com.swp391.clubmanagement.enums.ClubCategory;
import com.swp391.clubmanagement.enums.RequestStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "ClubApplications")
public class ClubApplications {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    Integer requestId;
    
    @ManyToOne
    @JoinColumn(name = "creator_id", nullable = false)
    Users creator;
    
    @OneToOne
    @JoinColumn(name = "club_id")
    Clubs club;
    
    // Thông tin CLB dự kiến
    @Column(name = "proposed_name", nullable = false)
    String proposedName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    ClubCategory category;
    
    @Column(name = "purpose", columnDefinition = "TEXT")
    String purpose;
    
    @Column(name = "description", columnDefinition = "TEXT")
    String description;
    
    @Column(name = "location")
    String location;
    
    @Column(name = "email")
    String email;
    
    @Column(name = "default_membership_fee", precision = 10, scale = 2)
    BigDecimal defaultMembershipFee; // Phí cho gói membership mặc định
    
    // Trạng thái duyệt của Admin
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    RequestStatus status = RequestStatus.DangCho;
    
    @Column(name = "admin_note", columnDefinition = "TEXT")
    String adminNote;
    
    @ManyToOne
    @JoinColumn(name = "reviewer_id")
    Users reviewer;
    
    @Column(name = "created_at")
    @Builder.Default
    LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    LocalDateTime updatedAt;
}

