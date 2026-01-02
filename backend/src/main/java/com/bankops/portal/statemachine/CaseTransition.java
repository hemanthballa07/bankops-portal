package com.bankops.portal.statemachine;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Defines all allowed state transitions in the case lifecycle.
 * Each transition specifies from/to states, display labels, and validation
 * requirements.
 */
@Getter
public enum CaseTransition {
    // From NEW
    ASSIGN(CaseState.NEW, CaseState.ASSIGNED, "Assign", true, false),
    QUICK_RESOLVE_NEW(CaseState.NEW, CaseState.RESOLVED, "Quick Resolve", false, true),

    // From ASSIGNED
    START_INVESTIGATION(CaseState.ASSIGNED, CaseState.IN_PROGRESS, "Start Investigation", false, false),
    UNASSIGN(CaseState.ASSIGNED, CaseState.NEW, "Unassign", false, false),
    QUICK_RESOLVE_ASSIGNED(CaseState.ASSIGNED, CaseState.RESOLVED, "Quick Resolve", false, true),

    // From IN_PROGRESS
    REQUEST_CUSTOMER_INFO(CaseState.IN_PROGRESS, CaseState.PENDING_CUSTOMER, "Request Customer Info", false, false),
    RESOLVE(CaseState.IN_PROGRESS, CaseState.RESOLVED, "Resolve", false, true),
    REASSIGN(CaseState.IN_PROGRESS, CaseState.ASSIGNED, "Reassign", true, false),

    // From PENDING_CUSTOMER
    RESUME_INVESTIGATION(CaseState.PENDING_CUSTOMER, CaseState.IN_PROGRESS, "Resume Investigation", false, false),
    RESOLVE_FROM_PENDING(CaseState.PENDING_CUSTOMER, CaseState.RESOLVED, "Resolve", false, true),

    // From RESOLVED
    CLOSE(CaseState.RESOLVED, CaseState.CLOSED, "Close Case", false, false),
    REOPEN(CaseState.RESOLVED, CaseState.IN_PROGRESS, "Reopen", false, false);

    // CLOSED is terminal - no transitions out

    private final CaseState fromState;
    private final CaseState toState;
    private final String label;
    private final boolean requiresAssignee;
    private final boolean requiresResolution;

    CaseTransition(CaseState fromState, CaseState toState, String label,
            boolean requiresAssignee, boolean requiresResolution) {
        this.fromState = fromState;
        this.toState = toState;
        this.label = label;
        this.requiresAssignee = requiresAssignee;
        this.requiresResolution = requiresResolution;
    }

    /**
     * Get all transitions available from a given state
     */
    public static List<CaseTransition> getTransitionsFrom(CaseState state) {
        return Arrays.stream(values())
                .filter(t -> t.fromState == state)
                .collect(Collectors.toList());
    }

    /**
     * Find a transition between two states
     */
    public static CaseTransition findTransition(CaseState from, CaseState to) {
        return Arrays.stream(values())
                .filter(t -> t.fromState == from && t.toState == to)
                .findFirst()
                .orElse(null);
    }

    /**
     * Check if a transition exists between two states
     */
    public static boolean isValidTransition(CaseState from, CaseState to) {
        return findTransition(from, to) != null;
    }
}
