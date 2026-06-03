package com.bankops.portal.dto;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Ops-wide analytics aggregates for the Reports screen. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportSummaryDto {
    private Map<String, Long> transactionsByStatus;
    private Map<String, Long> casesByState;
    private Map<String, Long> casesBySeverity;
    private Map<String, Long> mlRiskByBand;
    private CaseKpiDto caseKpis;
    private long totalTransactions;
    private long totalCases;
}
