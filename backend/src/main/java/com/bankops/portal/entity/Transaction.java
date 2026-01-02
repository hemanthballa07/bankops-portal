package com.bankops.portal.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "transactions", uniqueConstraints = @UniqueConstraint(name = "uk_txn_account_idemp_type", columnNames = {
        "account_id", "idempotency_key", "type" }))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(name = "correlation_id", nullable = false, unique = true, length = 36)
    private String correlationId;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TransactionCategory category = TransactionCategory.OTHER;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SupportCase> cases = new ArrayList<>();

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LogEvent> logEvents = new ArrayList<>();

    public enum TransactionType {
        DEPOSIT, WITHDRAWAL
    }

    public enum TransactionStatus {
        PENDING, COMPLETED, FAILED
    }

    public enum TransactionCategory {
        GROCERIES, DINING, TRANSPORTATION, UTILITIES,
        ENTERTAINMENT, HEALTHCARE, SHOPPING, OTHER
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
    }
}



