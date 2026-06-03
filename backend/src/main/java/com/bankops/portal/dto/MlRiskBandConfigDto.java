package com.bankops.portal.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The chip's current effective band thresholds (override or defaults). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MlRiskBandConfigDto {
    private double medThreshold;
    private double highThreshold;
    private LocalDateTime updatedAt; // null when defaults (no override row)
}
