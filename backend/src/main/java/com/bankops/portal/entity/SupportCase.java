package com.bankops.portal.entity;

import com.bankops.portal.sla.SlaPriority;
import com.bankops.portal.sla.SlaStatus;
import com.bankops.portal.statemachine.CaseState;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cases")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CaseState state = CaseState.NEW;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CaseSeverity severity = CaseSeverity.MEDIUM;

    @Column(nullable = false, length = 1000)
    private String summary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "assigned_to")
    private String assignedTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private Agent assignee;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    // ========== SLA Fields ==========

    @Column(name = "priority")
    @Enumerated(EnumType.STRING)
    private SlaPriority priority;

    @Column(name = "sla_due_at")
    private LocalDateTime slaDueAt;

    @Column(name = "sla_status")
    @Enumerated(EnumType.STRING)
    private SlaStatus slaStatus;

    @Column(name = "paused_at")
    private LocalDateTime pausedAt;

    @Column(name = "total_paused_duration_seconds")
    @Builder.Default
    private Long totalPausedDurationSeconds = 0L;

    @OneToMany(mappedBy = "supportCase", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CaseNote> notes = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "case_transactions", joinColumns = @JoinColumn(name = "case_id"), inverseJoinColumns = @JoinColumn(name = "transaction_id"))
    @Builder.Default
    private List<Transaction> relatedTransactions = new ArrayList<>();

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(length = 2000)
    private String resolution;

    /**
     * @deprecated Use CaseState enum instead
     */
    @Deprecated
    public enum CaseStatus {
        OPEN, INVESTIGATING, RESOLVED
    }

    public enum CaseSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
