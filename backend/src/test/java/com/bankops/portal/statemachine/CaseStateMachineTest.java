package com.bankops.portal.statemachine;

import com.bankops.portal.entity.SupportCase;
import com.bankops.portal.entity.StateTransitionEvent;
import com.bankops.portal.repository.StateTransitionEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for the CaseStateMachine.
 * Tests all 13 allowed transitions, invalid transitions, and business rule
 * validation.
 */
@ExtendWith(MockitoExtension.class)
class CaseStateMachineTest {

    @Mock
    private StateTransitionEventRepository transitionEventRepository;

    @InjectMocks
    private CaseStateMachine stateMachine;

    private SupportCase testCase;

    @BeforeEach
    void setUp() {
        testCase = SupportCase.builder()
                .id(1L)
                .state(CaseState.NEW)
                .severity(SupportCase.CaseSeverity.HIGH)
                .summary("Test case")
                .build();
    }

    // ========== Valid Transition Tests ==========

    /**
     * Parameterized test for all 13 valid transitions
     */
    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("provideValidTransitions")
    void testValidTransitions(CaseState fromState, CaseState toState, String transitionName) {
        // Given
        testCase.setState(fromState);

        // Add required fields based on transition requirements
        if (transitionName.contains("ASSIGN") || transitionName.contains("REASSIGN")) {
            testCase.setAssignedTo("agent1");
        }
        if (transitionName.contains("RESOLVE")) {
            testCase.setResolution("Issue fixed");
        }


        // When & Then - should not throw exception
        assertDoesNotThrow(() -> stateMachine.validateTransition(fromState, toState));

    }

    static Stream<Arguments> provideValidTransitions() {
        return Stream.of(
                // From NEW
                Arguments.of(CaseState.NEW, CaseState.ASSIGNED, "ASSIGN"),
                Arguments.of(CaseState.NEW, CaseState.RESOLVED, "QUICK_RESOLVE_NEW"),

                // From ASSIGNED
                Arguments.of(CaseState.ASSIGNED, CaseState.IN_PROGRESS, "START_INVESTIGATION"),
                Arguments.of(CaseState.ASSIGNED, CaseState.NEW, "UNASSIGN"),
                Arguments.of(CaseState.ASSIGNED, CaseState.RESOLVED, "QUICK_RESOLVE_ASSIGNED"),

                // From IN_PROGRESS
                Arguments.of(CaseState.IN_PROGRESS, CaseState.PENDING_CUSTOMER, "REQUEST_CUSTOMER_INFO"),
                Arguments.of(CaseState.IN_PROGRESS, CaseState.RESOLVED, "RESOLVE"),
                Arguments.of(CaseState.IN_PROGRESS, CaseState.ASSIGNED, "REASSIGN"),

                // From PENDING_CUSTOMER
                Arguments.of(CaseState.PENDING_CUSTOMER, CaseState.IN_PROGRESS, "RESUME_INVESTIGATION"),
                Arguments.of(CaseState.PENDING_CUSTOMER, CaseState.RESOLVED, "RESOLVE_FROM_PENDING"),

                // From RESOLVED
                Arguments.of(CaseState.RESOLVED, CaseState.CLOSED, "CLOSE"),
                Arguments.of(CaseState.RESOLVED, CaseState.IN_PROGRESS, "REOPEN"));
    }

    // ========== Invalid Transition Tests ==========

    @ParameterizedTest(name = "{0} -> {1} should fail")
    @MethodSource("provideInvalidTransitions")
    void testInvalidTransitions(CaseState fromState, CaseState toState) {
        // Given
        testCase.setState(fromState);

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            stateMachine.validateTransition(fromState, toState);
        });

        assertTrue(exception.getMessage().contains("Invalid state transition") ||
                exception.getMessage().contains("terminal state"));
    }

    static Stream<Arguments> provideInvalidTransitions() {
        return Stream.of(
                // From CLOSED (terminal state)
                Arguments.of(CaseState.CLOSED, CaseState.NEW),
                Arguments.of(CaseState.CLOSED, CaseState.ASSIGNED),
                Arguments.of(CaseState.CLOSED, CaseState.IN_PROGRESS),
                Arguments.of(CaseState.CLOSED, CaseState.RESOLVED),

                // Invalid backwards transitions
                Arguments.of(CaseState.RESOLVED, CaseState.NEW),
                Arguments.of(CaseState.RESOLVED, CaseState.ASSIGNED),
                Arguments.of(CaseState.IN_PROGRESS, CaseState.NEW),

                // Skipping states
                Arguments.of(CaseState.NEW, CaseState.IN_PROGRESS),
                Arguments.of(CaseState.NEW, CaseState.PENDING_CUSTOMER),
                Arguments.of(CaseState.NEW, CaseState.CLOSED),
                Arguments.of(CaseState.ASSIGNED, CaseState.PENDING_CUSTOMER),
                Arguments.of(CaseState.ASSIGNED, CaseState.CLOSED));
    }

    // ========== Terminal State Tests ==========

    @Test
    void testClosedState_IsTerminal() {
        // Given
        testCase.setState(CaseState.CLOSED);

        // When
        List<CaseTransition> allowedTransitions = stateMachine.getAllowedTransitions(CaseState.CLOSED, testCase);

        // Then
        assertTrue(allowedTransitions.isEmpty(), "CLOSED state should have no allowed transitions");
        assertTrue(CaseState.CLOSED.isTerminal(), "CLOSED should be marked as terminal");
    }

    @Test
    void testTransitionFromClosedState_ThrowsException() {
        // Given
        testCase.setState(CaseState.CLOSED);

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            stateMachine.validateTransition(CaseState.CLOSED, CaseState.NEW);
        });

        assertTrue(exception.getMessage().contains("terminal state"));
    }

    // ========== Business Rule Validation Tests ==========

    @Test
    void testAssignTransition_RequiresAssignee() {
        // Given
        testCase.setState(CaseState.NEW);
        testCase.setAssignedTo(null); // No assignee

        // When
        List<CaseTransition> allowedTransitions = stateMachine.getAllowedTransitions(CaseState.NEW, testCase);

        // Then
        boolean assignTransitionAllowed = allowedTransitions.stream()
                .anyMatch(t -> t == CaseTransition.ASSIGN);
        assertFalse(assignTransitionAllowed, "ASSIGN transition should not be allowed without assignee");
    }

    @Test
    void testAssignTransition_WithAssignee_IsAllowed() {
        // Given
        testCase.setState(CaseState.NEW);
        testCase.setAssignedTo("agent1");

        // When
        List<CaseTransition> allowedTransitions = stateMachine.getAllowedTransitions(CaseState.NEW, testCase);

        // Then
        boolean assignTransitionAllowed = allowedTransitions.stream()
                .anyMatch(t -> t == CaseTransition.ASSIGN);
        assertTrue(assignTransitionAllowed, "ASSIGN transition should be allowed with assignee");
    }

    @Test
    void testResolveTransition_RequiresResolution() {
        // Given
        testCase.setState(CaseState.IN_PROGRESS);
        testCase.setResolution(null); // No resolution

        // When
        List<CaseTransition> allowedTransitions = stateMachine.getAllowedTransitions(CaseState.IN_PROGRESS, testCase);

        // Then
        boolean resolveTransitionAllowed = allowedTransitions.stream()
                .anyMatch(t -> t == CaseTransition.RESOLVE);
        assertFalse(resolveTransitionAllowed, "RESOLVE transition should not be allowed without resolution");
    }

    @Test
    void testResolveTransition_WithResolution_IsAllowed() {
        // Given
        testCase.setState(CaseState.IN_PROGRESS);
        testCase.setResolution("Issue fixed");

        // When
        List<CaseTransition> allowedTransitions = stateMachine.getAllowedTransitions(CaseState.IN_PROGRESS, testCase);

        // Then
        boolean resolveTransitionAllowed = allowedTransitions.stream()
                .anyMatch(t -> t == CaseTransition.RESOLVE);
        assertTrue(resolveTransitionAllowed, "RESOLVE transition should be allowed with resolution");
    }

    // ========== Audit Logging Tests ==========

    @Test
    void testExecuteTransition_CreatesAuditEvent() {
        // Given
        testCase.setState(CaseState.NEW);
        testCase.setAssignedTo("agent1");
        CaseTransition transition = CaseTransition.ASSIGN;
        String correlationId = "test-correlation-123";
        String actor = "admin";
        String reason = "Assigning to agent";

        when(transitionEventRepository.save(any(StateTransitionEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        CaseState result = stateMachine.executeTransition(testCase, transition, actor, correlationId, reason);

        // Then
        assertEquals(CaseState.ASSIGNED, result);

        ArgumentCaptor<StateTransitionEvent> eventCaptor = ArgumentCaptor.forClass(StateTransitionEvent.class);
        verify(transitionEventRepository).save(eventCaptor.capture());

        StateTransitionEvent savedEvent = eventCaptor.getValue();
        assertEquals(1L, savedEvent.getCaseId());
        assertEquals("NEW", savedEvent.getFromState());
        assertEquals("ASSIGNED", savedEvent.getToState());
        assertEquals("ASSIGN", savedEvent.getTransitionType());
        assertEquals(actor, savedEvent.getActor());
        assertEquals(correlationId, savedEvent.getCorrelationId());
        assertEquals(reason, savedEvent.getReason());
        assertNotNull(savedEvent.getTimestamp());
    }

    @Test
    void testCorrelationIdGeneration() {
        // When
        String correlationId1 = stateMachine.generateCorrelationId();
        String correlationId2 = stateMachine.generateCorrelationId();

        // Then
        assertNotNull(correlationId1);
        assertNotNull(correlationId2);
        assertTrue(correlationId1.startsWith("case-"));
        assertTrue(correlationId2.startsWith("case-"));
        assertNotEquals(correlationId1, correlationId2, "Correlation IDs should be unique");
    }

    // ========== Same-State Transition Test ==========

    @Test
    void testSameStateTransition_IsAllowed() {
        // Given
        testCase.setState(CaseState.IN_PROGRESS);

        // When & Then
        assertDoesNotThrow(() -> {
            stateMachine.validateTransition(CaseState.IN_PROGRESS, CaseState.IN_PROGRESS);
        });
    }

    // ========== Get Allowed Transitions Tests ==========

    @Test
    void testGetAllowedTransitions_NewState() {
        // Given
        testCase.setState(CaseState.NEW);
        testCase.setAssignedTo("agent1");
        testCase.setResolution("Quick fix");

        // When
        List<CaseTransition> allowedTransitions = stateMachine.getAllowedTransitions(CaseState.NEW, testCase);

        // Then
        assertEquals(2, allowedTransitions.size());
        assertTrue(allowedTransitions.contains(CaseTransition.ASSIGN));
        assertTrue(allowedTransitions.contains(CaseTransition.QUICK_RESOLVE_NEW));
    }

    @Test
    void testGetAllowedTransitions_InProgressState() {
        // Given
        testCase.setState(CaseState.IN_PROGRESS);
        testCase.setAssignedTo("agent1");
        testCase.setResolution("Issue fixed");

        // When
        List<CaseTransition> allowedTransitions = stateMachine.getAllowedTransitions(CaseState.IN_PROGRESS, testCase);

        // Then
        assertEquals(3, allowedTransitions.size());
        assertTrue(allowedTransitions.contains(CaseTransition.REQUEST_CUSTOMER_INFO));
        assertTrue(allowedTransitions.contains(CaseTransition.RESOLVE));
        assertTrue(allowedTransitions.contains(CaseTransition.REASSIGN));
    }
}
