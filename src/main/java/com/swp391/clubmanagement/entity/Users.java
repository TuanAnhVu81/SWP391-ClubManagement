package com.swp391.clubmanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "Users")
public class Users {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    String userId;
    
    @Column(name = "student_code", unique = true, nullable = false)
    String studentCode;
    
    @Column(name = "full_name", nullable = false)
    String fullName;
    
    @Column(name = "major")
    String major;
    
    @Column(name = "phone_number", length = 15)
    String phoneNumber;
    
    @Column(name = "avatar_url")
    String avatarUrl;
    
    @Column(name = "email", nullable = false)
    String email;

    @JsonIgnore
    @Column(name = "password")
    String password;

    @JsonIgnore
    @Column(name = "verification_code")
    String verificationCode;

    @JsonIgnore
    @Column(name = "verification_expiry")
    LocalDateTime verificationExpiry;

    @Column(name = "is_enabled")
    @Builder.Default
    boolean enabled = false;
    
    @Column(name = "is_active")
    @Builder.Default
    Boolean isActive = true;
    
    @Column(name = "created_at")
    @Builder.Default
    LocalDateTime createdAt = LocalDateTime.now();
    
    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    Roles role;
    
    // Relationships - Ignore để tránh circular reference khi serialize JSON
    @JsonIgnore
    @OneToMany(mappedBy = "creator")
    Set<ClubApplications> createdApplications;
    
    @JsonIgnore
    @OneToMany(mappedBy = "reviewer")
    Set<ClubApplications> reviewedApplications;
    
    @JsonIgnore
    @OneToMany(mappedBy = "founder")
    Set<Clubs> foundedClubs;
    
    @JsonIgnore
    @OneToMany(mappedBy = "user")
    Set<Registers> registers;
    
    @JsonIgnore
    @OneToMany(mappedBy = "approver")
    Set<Registers> approvedRegisters;
}
