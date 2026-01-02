package com.bankops.portal.statemachine;

import com.bankops.portal.entity.StateTransitionEvent;
import com.bankops.portal.entity.SupportCase;
import com.bankops.portal.repository.StateTransitionEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core state machine service for case lifecycle management.
 * Handles state transition validation, execution, and audit logging.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CaseStateMachine {

    private final StateTransitionEventRepository transitionEventRepository;

    /**
     * Get all allowed transitions from the current state of a case.
     * Filters transitions based on business rules (e.g., requires assignee).
     *
     * @param currentState The current state of the case
     * @param caseEntity   The case entity (for checking business rules)
     * @return List of allowed transitions
     */
    public List<CaseTransition> getAllowedTransitions(CaseState currentState, SupportCase caseEntity) {
        if (currentState.isTerminal()) {
            return List.of(); // No transitions from terminal states
        }

        return CaseTransition.getTransitionsFrom(currentState).stream()
                .filter(transition -> isTransitionAllowed(transition, caseEntity))
                .collect(Collectors.toList());
    }

    /**
     * Check if a specific transition is allowed based on business rules.
     */
    private boolean isTransitionAllowed(CaseTransition transition, SupportCase caseEntity) {
        // Check if assignee is required but not present
        if (transition.isRequiresAssignee() &&
                (caseEntity.getAssignedTo() == null || caseEntity.getAssignedTo().trim().isEmpty())) {
            return false;
        }

        // Check if resolution is required but not present
        if (transition.isRequiresResolution() &&
                (caseEntity.getResolution() == null || caseEntity.getResolution().trim().isEmpty())) {
            return false;
        }

        return true;
    }

    /**
     * Validate that a state transition is allowed.
     * Throws exception if transition is invalid.
     *
     * @param from Current state
     * @param to   Target state
     * @throws IllegalStateException if transition is not allowed
     */
    public void validateTransition(CaseState from, CaseState to) {
        // Allow same-state transitions (no-op)
        if (from == to) {
            return;
        }

        // Check if from state is terminal
        if (from.isTerminal()) {
            throw new IllegalStateException(
                    String.format("Cannot transition from terminal state %s", from.getDisplayName()));
        }

        // Check if transition exists
        if (!CaseTransition.isValidTransition(from, to)) {
            throw new IllegalStateException(
                    String.format("Invalid state transition from %s to %s",
                            from.getDisplayName(), to.getDisplayName()));
        }
    }

    /**
     * Execute a state transition with full validation and audit logging.
     *
     * @param caseEntity    The case to transition
     * @param transition    The transition to execute
     * @param actor         The user performing the transition
     * @param correlationId Correlation ID for distributed tracing
     * @param reason        Optional reason for the transition
     * @return The new state
     */
    @Transactional
    public CaseState executeTransition(SupportCase caseEntity, CaseTransition transition,
            String actor, String correlationId, String reason) {
        CaseState currentState = caseEntity.getState();
        CaseState targetState = transition.getToState();

        // Validate the transition
        validateTransition(currentState, targetState);

        // Check business rules
        if (!isTransitionAllowed(transition, caseEntity)) {
            throw new IllegalStateException(
                    String.format("Transition %s not allowed: business rules not satisfied",
                            transition.getLabel()));
        }

        // Log the transition
        logTransition(caseEntity.getId(), currentState, targetState,
                transition.name(), actor, correlationId, reason);

        log.info("Case {} transitioned from {} to {} by {} (correlation: {})",
                caseEntity.getId(), currentState, targetState, actor, correlationId);

        return targetState;
    }

    /**
     * Execute a state transition by target state (finds appropriate transition).
     *
     * @param caseEntity    The case to transition
     * @param targetState   The desired target state
     * @param actor         The user performing the transition
     * @param correlationId Correlation ID for distributed tracing
     * @param reason        Optional reason for the transition
     * @return The new state
     */
    @Transactional
    public CaseState executeTransitionToState(SupportCase caseEntity, CaseState targetState,
            String actor, String correlationId, String reason) {
        CaseState currentState = caseEntity.getState();

        // Find the transition
        CaseTransition transition = CaseTransition.findTransition(currentState, targetState);
        if (transition == null) {
            throw new IllegalStateException(
                    String.format("No transition found from %s to %s",
                            currentState.getDisplayName(), targetState.getDisplayName()));
        }

        return executeTransition(caseEntity, transition, actor, correlationId, reason);
    }

    /**
     * Generate a new correlation ID for tracking.
     */
    public String generateCorrelationId() {
        return "case-" + UUID.randomUUID().toString();
    }

    /**
     * Log a state transition to the audit table.
     */
    private void logTransition(Long caseId, CaseState fromState, CaseState toState,
            String transitionType, String actor, String correlationId, String reason) {
        StateTransitionEvent event = StateTransitionEvent.builder()
                .caseId(caseId)
                .fromState(fromState.name())
                .toState(toState.name())
                .transitionType(transitionType)
                .actor(actor)
                .correlationId(correlationId)
                .timestamp(LocalDateTime.now())
                .reason(reason)
                .build();

        transitionEventRepository.save(event);
    }

    /**
     * Get the transition history for a case.
     */
    public List<StateTransitionEvent> getTransitionHistory(Long caseId) {
        return transitionEventRepository.findByCaseIdOrderByTimestampDesc(caseId);
    }

    /**
     * Get all transitions with a specific correlation ID.
     */
    public List<StateTransitionEvent> getTransitionsByCorrelationId(String correlationId) {
        return transitionEventRepository.findByCorrelationId(correlationId);
    }
}
