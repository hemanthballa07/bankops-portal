package com.bankops.portal.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Audit entity for tracking case state transitions.
 * Provides complete audit trail with correlation IDs for distributed tracing.
 */
@Entity
@Table(name = "state_transition_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StateTransitionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "from_state", nullable = false, length = 50)
    private String fromState;

    @Column(name = "to_state", nullable = false, length = 50)
    private String toState;

    @Column(name = "transition_type", length = 50)
    private String transitionType;

    @Column(name = "actor", nullable = false, length = 100)
    private String actor;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
