package com.bankops.portal.sla;

import com.bankops.portal.entity.SlaStatusChangeEvent;
import com.bankops.portal.entity.SupportCase;
import com.bankops.portal.repository.SlaStatusChangeEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Core service for SLA lifecycle management.
 * Handles initialization, pause/resume, status computation, and audit logging.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SlaService {

    private final SlaConfiguration slaConfiguration;
    private final SlaStatusChangeEventRepository slaEventRepository;
    private final SlaConfigService slaConfigService;
    private final Clock clock;

    /**
     * Initialize SLA when a case is created.
     * Sets initial due date and ON_TRACK status.
     */
    public void initializeSla(SupportCase supportCase, SlaPriority priority) {
        if (supportCase.getSlaDueAt() != null) {
            log.warn("SLA already initialized for case {}", supportCase.getId());
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Duration slaDuration = slaConfigService.getDuration(priority);

        supportCase.setPriority(priority);
        supportCase.setSlaDueAt(now.plus(slaDuration));
        supportCase.setSlaStatus(SlaStatus.ON_TRACK);
        supportCase.setTotalPausedDurationSeconds(0L);

        log.info("Initialized SLA for case {} with priority {} (due: {})",
                supportCase.getId(), priority, supportCase.getSlaDueAt());
    }

    /**
     * Pause SLA when entering PENDING_CUSTOMER state.
     * Records the pause timestamp.
     */
    public void pauseSla(SupportCase supportCase) {
        if (supportCase.getPausedAt() != null) {
            log.warn("SLA already paused for case {}", supportCase.getId());
            return;
        }

        if (supportCase.getSlaStatus() == null) {
            log.warn("Cannot pause SLA for case {} - SLA not initialized", supportCase.getId());
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        supportCase.setPausedAt(now);

        log.info("Paused SLA for case {} at {}", supportCase.getId(), now);
    }

    /**
     * Resume SLA when leaving PENDING_CUSTOMER state.
     * Accumulates pause duration and adjusts due date.
     */
    public void resumeSla(SupportCase supportCase) {
        if (supportCase.getPausedAt() == null) {
            log.warn("SLA not paused for case {}", supportCase.getId());
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime pausedAt = supportCase.getPausedAt();

        // Calculate pause duration
        Duration pauseDuration = Duration.between(pausedAt, now);
        long pauseSeconds = pauseDuration.getSeconds();

        // Accumulate total paused time
        long totalPaused = supportCase.getTotalPausedDurationSeconds() + pauseSeconds;
        supportCase.setTotalPausedDurationSeconds(totalPaused);

        // Adjust due date by adding pause duration
        LocalDateTime newDueAt = supportCase.getSlaDueAt().plus(pauseDuration);
        supportCase.setSlaDueAt(newDueAt);

        // Clear pause timestamp
        supportCase.setPausedAt(null);

        log.info("Resumed SLA for case {} (paused for {} seconds, new due: {})",
                supportCase.getId(), pauseSeconds, newDueAt);
    }

    /**
     * Stop SLA permanently when case is RESOLVED or CLOSED.
     * No further SLA updates will occur.
     */
    public void stopSla(SupportCase supportCase) {
        if (supportCase.getSlaStatus() == null) {
            return; // SLA not initialized
        }

        log.info("Stopped SLA for case {} in state {}",
                supportCase.getId(), supportCase.getState());
    }

    /**
     * Compute current SLA status based on elapsed time.
     * Returns ON_TRACK, AT_RISK, or BREACHED.
     */
    public SlaStatus computeSlaStatus(SupportCase supportCase) {
        if (supportCase.getSlaDueAt() == null || supportCase.getPriority() == null) {
            return null; // SLA not initialized
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime dueAt = supportCase.getSlaDueAt();

        // If currently paused, use pause time instead of now
        LocalDateTime effectiveNow = supportCase.getPausedAt() != null
                ? supportCase.getPausedAt()
                : now;

        // Check if breached (past due)
        if (effectiveNow.isAfter(dueAt)) {
            return SlaStatus.BREACHED;
        }

        // Calculate elapsed percentage
        LocalDateTime createdAt = supportCase.getCreatedAt();
        // Derive the SLA window from the case's own fields so status is independent of live config
        // (new cases pick up new durations via initializeSla; in-flight cases stay stable).
        // (slaDueAt - createdAt) - totalPaused == the duration used when the case was initialized.
        Duration totalDuration = Duration.between(createdAt, dueAt)
                .minusSeconds(supportCase.getTotalPausedDurationSeconds());
        if (totalDuration.isZero() || totalDuration.isNegative()) {
            return SlaStatus.ON_TRACK; // malformed window; not breached (breach checked above)
        }
        Duration elapsed = Duration.between(createdAt, effectiveNow);

        // Subtract paused time from elapsed
        long pausedSeconds = supportCase.getTotalPausedDurationSeconds();
        if (supportCase.getPausedAt() != null) {
            // Add current pause duration
            pausedSeconds += Duration.between(supportCase.getPausedAt(), now).getSeconds();
        }

        Duration effectiveElapsed = elapsed.minus(Duration.ofSeconds(pausedSeconds));
        double elapsedPercentage = (double) effectiveElapsed.getSeconds() / totalDuration.getSeconds();

        // Check AT_RISK threshold
        if (slaConfiguration.isAtRisk(elapsedPercentage)) {
            return SlaStatus.AT_RISK;
        }

        return SlaStatus.ON_TRACK;
    }

    /**
     * Update SLA status and create audit event if changed.
     * Returns true if status was updated.
     */
    @Transactional
    public boolean updateSlaStatus(SupportCase supportCase) {
        SlaStatus currentStatus = supportCase.getSlaStatus();
        SlaStatus newStatus = computeSlaStatus(supportCase);

        if (newStatus == null || newStatus == currentStatus) {
            return false; // No change
        }

        // Update status
        supportCase.setSlaStatus(newStatus);

        // Create audit event
        SlaStatusChangeEvent event = SlaStatusChangeEvent.builder()
                .caseId(supportCase.getId())
                .previousStatus(currentStatus)
                .newStatus(newStatus)
                .timestamp(LocalDateTime.now(clock))
                .correlationId(supportCase.getCorrelationId())
                .actor("SYSTEM")
                .build();

        slaEventRepository.save(event);

        log.info("SLA status changed for case {}: {} -> {} (correlation: {})",
                supportCase.getId(), currentStatus, newStatus, supportCase.getCorrelationId());

        return true;
    }

    /**
     * Calculate remaining time until SLA breach.
     * Returns negative duration if already breached.
     */
    public Duration getRemainingTime(SupportCase supportCase) {
        if (supportCase.getSlaDueAt() == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime effectiveNow = supportCase.getPausedAt() != null
                ? supportCase.getPausedAt()
                : now;

        return Duration.between(effectiveNow, supportCase.getSlaDueAt());
    }
}
