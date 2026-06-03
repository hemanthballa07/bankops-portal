package com.bankops.portal.sla;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bankops.portal.dto.SlaConfigDto;
import com.bankops.portal.entity.SlaConfig;
import com.bankops.portal.repository.SlaConfigRepository;

import lombok.RequiredArgsConstructor;

/**
 * Runtime-configurable SLA durations. An override row in {@code sla_config} wins; otherwise
 * the {@link SlaPriority} enum default applies (so behaviour is unchanged until an admin edits).
 */
@Service
@RequiredArgsConstructor
public class SlaConfigService {

    /** Upper bound for a configured duration: 365 days. */
    private static final long MAX_DURATION_SECONDS = 365L * 24 * 60 * 60;

    private final SlaConfigRepository repository;
    private final Clock clock;

    /** Effective duration for a priority: DB override if present, else the enum default. */
    public Duration getDuration(SlaPriority priority) {
        return repository.findById(priority.name())
                .map(c -> Duration.ofSeconds(c.getDurationSeconds()))
                .orElse(priority.getSlaDuration());
    }

    /** All priorities with their current effective durations (override or enum default). */
    public List<SlaConfigDto> list() {
        List<SlaConfigDto> out = new ArrayList<>();
        for (SlaPriority p : SlaPriority.values()) {
            SlaConfig row = repository.findById(p.name()).orElse(null);
            out.add(SlaConfigDto.builder()
                    .priority(p.name())
                    .durationSeconds(row != null ? row.getDurationSeconds() : p.getSlaDurationSeconds())
                    .updatedAt(row != null ? row.getUpdatedAt() : null)
                    .build());
        }
        return out;
    }

    @Transactional
    public SlaConfigDto update(String priority, long durationSeconds) {
        SlaPriority p;
        try {
            p = SlaPriority.valueOf(priority);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown SLA priority: " + priority);
        }
        if (durationSeconds <= 0 || durationSeconds > MAX_DURATION_SECONDS) {
            throw new IllegalArgumentException(
                    "durationSeconds must be between 1 and " + MAX_DURATION_SECONDS);
        }
        SlaConfig row = repository.findById(p.name())
                .orElse(SlaConfig.builder().priority(p.name()).build());
        row.setDurationSeconds(durationSeconds);
        row.setUpdatedAt(LocalDateTime.now(clock));
        repository.save(row);
        return SlaConfigDto.builder()
                .priority(p.name())
                .durationSeconds(durationSeconds)
                .updatedAt(row.getUpdatedAt())
                .build();
    }
}
