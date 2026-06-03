package com.bankops.portal.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.bankops.portal.dto.CaseKpiDto;
import com.bankops.portal.dto.MlRiskBandConfigDto;
import com.bankops.portal.dto.ReportSummaryDto;
import com.bankops.portal.repository.SupportCaseRepository;
import com.bankops.portal.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;

/** Builds the ops-wide analytics summary from GROUP BY counts + reused case KPIs. */
@Service
@RequiredArgsConstructor
public class ReportsService {

    private final TransactionRepository transactionRepository;
    private final SupportCaseRepository supportCaseRepository;
    private final CaseService caseService;
    private final MlRiskBandConfigService mlRiskBandConfigService;

    public ReportSummaryDto getSummary() {
        Map<String, Long> txByStatus = toCountMap(transactionRepository.countGroupedByStatus());
        Map<String, Long> casesByState = toCountMap(supportCaseRepository.countGroupedByState());
        Map<String, Long> casesBySeverity = toCountMap(supportCaseRepository.countGroupedBySeverity());
        CaseKpiDto kpis = caseService.getKpis();

        MlRiskBandConfigDto bands = mlRiskBandConfigService.getConfig();
        Map<String, Long> mlRiskByBand = new LinkedHashMap<>();
        mlRiskByBand.put("LOW", 0L);
        mlRiskByBand.put("MED", 0L);
        mlRiskByBand.put("HIGH", 0L);
        for (Double s : transactionRepository.findMlScoresAboveZero()) {
            String band = s >= bands.getHighThreshold() ? "HIGH"
                    : s >= bands.getMedThreshold() ? "MED" : "LOW";
            mlRiskByBand.merge(band, 1L, Long::sum);
        }

        return ReportSummaryDto.builder()
                .transactionsByStatus(txByStatus)
                .casesByState(casesByState)
                .casesBySeverity(casesBySeverity)
                .mlRiskByBand(mlRiskByBand)
                .caseKpis(kpis)
                .totalTransactions(sum(txByStatus))
                .totalCases(sum(casesByState))
                .build();
    }

    private long sum(Map<String, Long> m) {
        return m.values().stream().mapToLong(Long::longValue).sum();
    }

    /** Maps GROUP BY rows ([enum, count]) into a stable-ordered name->count map. */
    private Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            if (row[0] == null) {
                continue;
            }
            map.put(((Enum<?>) row[0]).name(), ((Number) row[1]).longValue());
        }
        return map;
    }
}
