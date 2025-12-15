package com.swp391.clubmanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.swp391.clubmanagement.enums.ClubCategory;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@EntityListeners(com.swp391.clubmanagement.configuration.EntityAuditListener.class)
@Table(name = "Clubs")
public class Clubs {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "club_id")
    Integer clubId;
    
    @Column(name = "club_name", nullable = false)
    String clubName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    ClubCategory category;
    
    @Column(name = "logo")
    String logo;
    
    @Column(name = "location")
    String location;
    
    @Column(name = "description", columnDefinition = "TEXT")
    String description;
    
    @Column(name = "email")
    String email;
    
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "founder_id")
    Users founder;
    
    @Column(name = "is_active")
    @Builder.Default
    Boolean isActive = true;
    
    @Column(name = "established_date")
    LocalDate establishedDate;
    
    // Relationships - Ignore để tránh circular reference khi serialize JSON
    @JsonIgnore
    @OneToOne(mappedBy = "club")
    ClubApplications application;
    
    @JsonIgnore
    @OneToMany(mappedBy = "club")
    Set<Memberships> memberships;
}

