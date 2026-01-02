package com.bankops.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Snapshot of case state at a specific point in time for replay functionality.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaseSnapshotDto {
    private LocalDateTime snapshotAt;
    private String state;
    private String assigneeName;
    private Long assigneeId;
    private String slaStatus;
    private LocalDateTime slaDueAt;
    private String severity;
    private LocalDateTime createdAt;
    private Map<String, Object> additionalFields;
}
