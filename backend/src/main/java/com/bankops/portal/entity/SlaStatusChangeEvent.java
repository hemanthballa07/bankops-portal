package com.bankops.portal.entity;

import com.bankops.portal.sla.SlaStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Audit entity for tracking SLA status changes.
 * Provides immutable audit trail of when cases move between SLA statuses.
 */
@Entity
@Table(name = "sla_status_change_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlaStatusChangeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "previous_status", length = 20)
    @Enumerated(EnumType.STRING)
    private SlaStatus previousStatus;

    @Column(name = "new_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SlaStatus newStatus;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "actor", nullable = false, length = 100)
    private String actor;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
