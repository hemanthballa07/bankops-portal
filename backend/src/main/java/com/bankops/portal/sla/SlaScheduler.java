package com.bankops.portal.sla;

import com.bankops.portal.entity.SupportCase;
import com.bankops.portal.repository.SupportCaseRepository;
import com.bankops.portal.statemachine.CaseState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Background scheduler for SLA status updates.
 * Runs periodically to check and update SLA statuses for active cases.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SlaScheduler {

    private final SupportCaseRepository caseRepository;
    private final SlaService slaService;

    /**
     * Scheduled job that runs every minute to update SLA statuses.
     * Only processes active cases (not RESOLVED or CLOSED).
     * Idempotent and safe to rerun.
     */
    @Scheduled(fixedRate = 60000) // Every 1 minute
    @Transactional
    public void updateSlaStatuses() {
        String correlationId = "sla-check-" + UUID.randomUUID();

        log.debug("Starting SLA status check (correlation: {})", correlationId);

        // Query only active cases that can have SLA changes
        List<CaseState> activeStates = List.of(
                CaseState.NEW,
                CaseState.ASSIGNED,
                CaseState.IN_PROGRESS,
                CaseState.PENDING_CUSTOMER);

        List<SupportCase> activeCases = caseRepository.findByStateIn(activeStates);

        int scanned = 0;
        int updated = 0;

        for (SupportCase supportCase : activeCases) {
            scanned++;

            try {
                if (slaService.updateSlaStatus(supportCase)) {
                    caseRepository.save(supportCase);
                    updated++;
                }
            } catch (Exception e) {
                log.error("Error updating SLA for case {}: {}",
                        supportCase.getId(), e.getMessage(), e);
            }
        }

        log.info("SLA check complete: {} cases scanned, {} updated (correlation: {})",
                scanned, updated, correlationId);
    }
}
