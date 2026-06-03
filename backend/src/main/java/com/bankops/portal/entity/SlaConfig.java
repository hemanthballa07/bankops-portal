package com.bankops.portal.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Optional runtime override of a SlaPriority's duration. Absence = use the enum default. */
@Entity
@Table(name = "sla_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlaConfig {

    @Id
    @Column(name = "priority", nullable = false, length = 10)
    private String priority; // P1 / P2 / P3 (SlaPriority.name())

    @Column(name = "duration_seconds", nullable = false)
    private long durationSeconds;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
