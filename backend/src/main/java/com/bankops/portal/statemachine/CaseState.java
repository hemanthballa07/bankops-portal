package com.bankops.portal.statemachine;

import lombok.Getter;

/**
 * Defines all possible states in the case lifecycle.
 * Each state has metadata for display, SLA calculation, and terminal status.
 */
@Getter
public enum CaseState {
    NEW("New", "Case created, awaiting triage", false, 1.0, "info"),
    ASSIGNED("Assigned", "Case assigned to an agent", false, 1.0, "primary"),
    IN_PROGRESS("In Progress", "Active investigation underway", false, 0.8, "warning"),
    PENDING_CUSTOMER("Pending Customer", "Waiting for customer response", false, 2.0, "secondary"),
    RESOLVED("Resolved", "Case resolved, awaiting closure", false, 0.5, "success"),
    CLOSED("Closed", "Case permanently closed", true, 0.0, "default");

    private final String displayName;
    private final String description;
    private final boolean terminal;
    private final double slaMultiplier;
    private final String colorIndicator;

    CaseState(String displayName, String description, boolean terminal,
            double slaMultiplier, String colorIndicator) {
        this.displayName = displayName;
        this.description = description;
        this.terminal = terminal;
        this.slaMultiplier = slaMultiplier;
        this.colorIndicator = colorIndicator;
    }

    /**
     * Check if this state is terminal (no further transitions allowed)
     */
    public boolean isTerminal() {
        return terminal;
    }

    /**
     * Get SLA multiplier for this state (affects time calculations)
     */
    public double getSlaMultiplier() {
        return slaMultiplier;
    }
}
