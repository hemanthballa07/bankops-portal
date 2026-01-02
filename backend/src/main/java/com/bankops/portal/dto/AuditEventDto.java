package com.bankops.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEventDto {

    private String id;
    private String entityType;
    private Long entityId;
    private String action;
    private String oldValue; // JSON string
    private String newValue; // JSON string
    private String performedBy;
    private LocalDateTime timestamp;
}
