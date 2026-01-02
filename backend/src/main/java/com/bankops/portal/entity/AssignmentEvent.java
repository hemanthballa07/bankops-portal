package com.bankops.portal.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Immutable audit entity for tracking assignment decisions.
 * Records who assigned what case to which agent, with decision reasoning.
 */
@Entity
@Table(name = "assignment_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "previous_assignee_id")
    private Long previousAssigneeId;

    @Column(name = "new_assignee_id")
    private Long newAssigneeId;

    @Column(name = "decision_policy_version", length = 10)
    @Builder.Default
    private String decisionPolicyVersion = "v1";

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "decision_inputs", columnDefinition = "JSON")
    private String decisionInputs; // JSON: {"severity": "HIGH", "slaStatus": "AT_RISK", "agentLoad": 5}

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(nullable = false, length = 100)
    private String actor; // "SYSTEM" or username

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
