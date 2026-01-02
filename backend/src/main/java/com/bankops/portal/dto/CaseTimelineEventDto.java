package com.bankops.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Unified timeline event DTO aggregating state changes, SLA changes, and
 * assignments.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaseTimelineEventDto {
    private Long id;
    private LocalDateTime timestamp;
    private EventType eventType;
    private String summary;
    private Map<String, Object> details;
    private String actor;
    private String correlationId;

    public enum EventType {
        STATE_CHANGE,
        SLA_CHANGE,
        ASSIGNMENT
    }
}
