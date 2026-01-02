package com.bankops.portal.sla;

import lombok.Getter;

import java.time.Duration;

/**
 * Priority levels with associated SLA durations.
 * Single source of truth for SLA time limits.
 */
@Getter
public enum SlaPriority {
    P1(Duration.ofHours(24)), // 24 hours - Critical
    P2(Duration.ofHours(72)), // 72 hours - High
    P3(Duration.ofDays(7)); // 7 days - Normal

    private final Duration slaDuration;

    SlaPriority(Duration slaDuration) {
        this.slaDuration = slaDuration;
    }

    /**
     * Get SLA duration in seconds for database storage
     */
    public long getSlaDurationSeconds() {
        return slaDuration.getSeconds();
    }
}
