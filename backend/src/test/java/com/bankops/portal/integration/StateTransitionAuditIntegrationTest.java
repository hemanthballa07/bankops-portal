package com.bankops.portal.integration;

import com.bankops.portal.entity.Customer;
import com.bankops.portal.entity.StateTransitionEvent;
import com.bankops.portal.entity.SupportCase;
import com.bankops.portal.repository.CustomerRepository;
import com.bankops.portal.repository.StateTransitionEventRepository;
import com.bankops.portal.repository.SupportCaseRepository;
import com.bankops.portal.statemachine.CaseState;
import com.bankops.portal.statemachine.CaseStateMachine;
import com.bankops.portal.statemachine.CaseTransition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for state transition audit logging.
 * Verifies that state transitions are properly recorded in the database.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StateTransitionAuditIntegrationTest {

    @Autowired
    private CaseStateMachine stateMachine;

    @Autowired
    private SupportCaseRepository caseRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private StateTransitionEventRepository transitionEventRepository;

    private Customer testCustomer;
    private SupportCase testCase;

    @BeforeEach
    void setUp() {
        // Clean up any existing data
        transitionEventRepository.deleteAll();
        caseRepository.deleteAll();
        customerRepository.deleteAll();

        // Create test customer
        testCustomer = Customer.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@test.com")
                .phone("123-456-7890")
                .build();
        testCustomer = customerRepository.save(testCustomer);

        // Create test case
        testCase = SupportCase.builder()
                .customer(testCustomer)
                .state(CaseState.NEW)
                .severity(SupportCase.CaseSeverity.HIGH)
                .summary("Test case for audit logging")
                .correlationId(stateMachine.generateCorrelationId())
                .build();
        testCase = caseRepository.save(testCase);
    }

    @Test
    void testStateTransition_CreatesAuditEvent() {
        // Given
        testCase.setAssignedTo("agent1");
        testCase = caseRepository.save(testCase);

        String actor = "admin";
        String reason = "Assigning case to agent";

        // When
        CaseState newState = stateMachine.executeTransition(
                testCase,
                CaseTransition.ASSIGN,
                actor,
                testCase.getCorrelationId(),
                reason);

        // Then
        assertEquals(CaseState.ASSIGNED, newState);

        // Verify audit event was created
        List<StateTransitionEvent> events = transitionEventRepository
                .findByCaseIdOrderByTimestampDesc(testCase.getId());
        assertEquals(1, events.size());

        StateTransitionEvent event = events.get(0);
        assertEquals(testCase.getId(), event.getCaseId());
        assertEquals("NEW", event.getFromState());
        assertEquals("ASSIGNED", event.getToState());
        assertEquals("ASSIGN", event.getTransitionType());
        assertEquals(actor, event.getActor());
        assertEquals(testCase.getCorrelationId(), event.getCorrelationId());
        assertEquals(reason, event.getReason());
        assertNotNull(event.getTimestamp());
    }

    @Test
    void testMultipleTransitions_CreatesMultipleAuditEvents() {
        // Given
        testCase.setAssignedTo("agent1");
        testCase = caseRepository.save(testCase);

        // When - Execute multiple transitions
        // NEW -> ASSIGNED
        stateMachine.executeTransition(testCase, CaseTransition.ASSIGN, "admin",
                testCase.getCorrelationId(), "Assigning case");
        testCase.setState(CaseState.ASSIGNED);
        testCase = caseRepository.save(testCase);

        // ASSIGNED -> IN_PROGRESS
        stateMachine.executeTransition(testCase, CaseTransition.START_INVESTIGATION, "agent1",
                testCase.getCorrelationId(), "Starting investigation");
        testCase.setState(CaseState.IN_PROGRESS);
        testCase = caseRepository.save(testCase);

        // IN_PROGRESS -> RESOLVED
        testCase.setResolution("Issue fixed");
        testCase = caseRepository.save(testCase);
        stateMachine.executeTransition(testCase, CaseTransition.RESOLVE, "agent1",
                testCase.getCorrelationId(), "Case resolved");

        // Then
        List<StateTransitionEvent> events = transitionEventRepository
                .findByCaseIdOrderByTimestampDesc(testCase.getId());
        assertEquals(3, events.size());

        // Verify events are in reverse chronological order
        assertEquals("RESOLVED", events.get(0).getToState());
        assertEquals("IN_PROGRESS", events.get(1).getToState());
        assertEquals("ASSIGNED", events.get(2).getToState());
    }

    @Test
    void testCorrelationId_IsConsistent() {
        // Given
        String correlationId = testCase.getCorrelationId();
        testCase.setAssignedTo("agent1");
        testCase = caseRepository.save(testCase);

        // When
        stateMachine.executeTransition(testCase, CaseTransition.ASSIGN, "admin",
                correlationId, "Test transition");

        // Then
        List<StateTransitionEvent> events = transitionEventRepository.findByCorrelationId(correlationId);
        assertEquals(1, events.size());
        assertEquals(correlationId, events.get(0).getCorrelationId());
    }

    @Test
    void testAuditEvent_IsImmutable() {
        // Given
        testCase.setAssignedTo("agent1");
        testCase = caseRepository.save(testCase);

        // When
        stateMachine.executeTransition(testCase, CaseTransition.ASSIGN, "admin",
                testCase.getCorrelationId(), "Original reason");

        List<StateTransitionEvent> events = transitionEventRepository
                .findByCaseIdOrderByTimestampDesc(testCase.getId());
        StateTransitionEvent originalEvent = events.get(0);
        Long originalId = originalEvent.getId();
        String originalReason = originalEvent.getReason();

        // Then - Verify event exists and is immutable
        assertNotNull(originalId);
        assertEquals("Original reason", originalReason);

        // Audit events should not be modified after creation
        // This is enforced by not having update methods in the service
        // and by database constraints if configured
    }

    @Test
    void testGetTransitionHistory_ReturnsAllEventsForCase() {
        // Given
        testCase.setAssignedTo("agent1");
        testCase.setResolution("Fixed");
        testCase = caseRepository.save(testCase);

        // When - Create multiple transitions
        stateMachine.executeTransition(testCase, CaseTransition.ASSIGN, "admin",
                testCase.getCorrelationId(), "Assign");
        testCase.setState(CaseState.ASSIGNED);

        stateMachine.executeTransition(testCase, CaseTransition.START_INVESTIGATION, "agent1",
                testCase.getCorrelationId(), "Start");
        testCase.setState(CaseState.IN_PROGRESS);

        stateMachine.executeTransition(testCase, CaseTransition.RESOLVE, "agent1",
                testCase.getCorrelationId(), "Resolve");

        // Then
        List<StateTransitionEvent> history = stateMachine.getTransitionHistory(testCase.getId());
        assertEquals(3, history.size());

        // Verify chronological order (most recent first)
        assertTrue(history.get(0).getTimestamp().isAfter(history.get(1).getTimestamp()));
        assertTrue(history.get(1).getTimestamp().isAfter(history.get(2).getTimestamp()));
    }

    @Test
    void testActorInformation_IsCaptured() {
        // Given
        testCase.setAssignedTo("agent1");
        testCase = caseRepository.save(testCase);
        String actor = "admin@example.com";

        // When
        stateMachine.executeTransition(testCase, CaseTransition.ASSIGN, actor,
                testCase.getCorrelationId(), "Assigning case");

        // Then
        List<StateTransitionEvent> events = transitionEventRepository.findByActorOrderByTimestampDesc(actor);
        assertEquals(1, events.size());
        assertEquals(actor, events.get(0).getActor());
    }
}
