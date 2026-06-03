package com.bankops.portal.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A priority's current effective SLA duration (override or enum default). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlaConfigDto {
    private String priority;
    private long durationSeconds;
    private LocalDateTime updatedAt; // null when value is the enum default (no override row)
}
