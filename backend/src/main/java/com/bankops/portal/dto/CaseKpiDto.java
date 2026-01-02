package com.bankops.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for case KPI dashboard metrics.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaseKpiDto {
    private int openCases;
    private int unassignedCases;
    private int slaAtRiskCases;
    private int highSeverityCases;
    private int unassignedHighSeverity;
}
