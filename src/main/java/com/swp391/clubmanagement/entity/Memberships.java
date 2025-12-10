package com.swp391.clubmanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "Memberships")
public class Memberships {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "package_id")
    Integer packageId;
    
    @ManyToOne
    @JoinColumn(name = "club_id", nullable = false)
    Clubs club;
    
    @Column(name = "package_name", nullable = false)
    String packageName;
    
    @Column(name = "term", nullable = false)
    String term;
    
    @Column(name = "price", precision = 10, scale = 2)
    @Builder.Default
    BigDecimal price = BigDecimal.ZERO;
    
    @Column(name = "description", columnDefinition = "TEXT")
    String description;
    
    @Column(name = "is_active")
    @Builder.Default
    Boolean isActive = true;
    
    @Column(name = "created_at")
    @Builder.Default
    LocalDateTime createdAt = LocalDateTime.now();
    
    // Relationships - Ignore để tránh circular reference khi serialize JSON
    @JsonIgnore
    @OneToMany(mappedBy = "membershipPackage")
    Set<Registers> registers;
}

