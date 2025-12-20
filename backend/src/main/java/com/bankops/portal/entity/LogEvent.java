package com.bankops.portal.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "log_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "correlation_id", nullable = false, length = 36, index = true)
    private String correlationId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LogLevel level;
    
    @Column(nullable = false, length = 2000)
    private String message;
    
    @Column(name = "context_json", columnDefinition = "TEXT")
    private String contextJson;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;
    
    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

