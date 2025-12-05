package com.swp391.clubmanagement.entity;

import com.swp391.clubmanagement.enums.ClubCategory;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
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
    
    @Column(name = "location")
    String location;
    
    @Column(name = "description", columnDefinition = "TEXT")
    String description;
    
    @ManyToOne
    @JoinColumn(name = "founder_id")
    Users founder;
    
    @Column(name = "is_active")
    @Builder.Default
    Boolean isActive = true;
    
    @Column(name = "established_date")
    @Builder.Default
    LocalDate establishedDate = LocalDate.now();
    
    // Relationships
    @OneToOne(mappedBy = "club")
    ClubApplications application;
    
    @OneToMany(mappedBy = "club")
    Set<Memberships> memberships;
}

