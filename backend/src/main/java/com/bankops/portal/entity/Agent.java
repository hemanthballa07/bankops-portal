package com.bankops.portal.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent entity representing support agents who can be assigned cases.
 * Includes optimistic locking for concurrency safety.
 */
@Entity
@Table(name = "agents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "max_active_cases", nullable = false)
    @Builder.Default
    private Integer maxActiveCases = 10;

    @Column(name = "skills", columnDefinition = "JSON")
    private String skills; // JSON array: ["high_severity", "fraud"]

    @Version
    private Long version; // Optimistic locking for concurrency safety

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Derived field for current active case count (not persisted).
     * Must be set by service layer.
     */
    @Transient
    private Integer currentActiveCount;
}
