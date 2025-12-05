package com.swp391.clubmanagement.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    Integer userId;
    
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
    
    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    Roles role;
    
    // Relationships
    @OneToMany(mappedBy = "creator")
    Set<ClubApplications> createdApplications;
    
    @OneToMany(mappedBy = "reviewer")
    Set<ClubApplications> reviewedApplications;
    
    @OneToMany(mappedBy = "founder")
    Set<Clubs> foundedClubs;
    
    @OneToMany(mappedBy = "user")
    Set<Registers> registers;
    
    @OneToMany(mappedBy = "approver")
    Set<Registers> approvedRegisters;
}
