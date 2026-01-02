package com.bankops.portal.assignment;

import com.bankops.portal.entity.Agent;
import com.bankops.portal.entity.AssignmentEvent;
import com.bankops.portal.entity.SupportCase;
import com.bankops.portal.repository.AgentRepository;
import com.bankops.portal.repository.AssignmentEventRepository;
import com.bankops.portal.repository.SupportCaseRepository;
import com.bankops.portal.statemachine.CaseState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AssignmentService with deterministic behavior.
 */
@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private SupportCaseRepository caseRepository;

    @Mock
    private AssignmentEventRepository assignmentEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AssignmentService assignmentService;

    private Clock fixedClock;
    private SupportCase testCase;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-01-02T12:00:00Z"), ZoneId.systemDefault());
        assignmentService = new AssignmentService(agentRepository, caseRepository,
                assignmentEventRepository, fixedClock, objectMapper);

        testCase = SupportCase.builder()
                .id(1L)
                .state(CaseState.NEW)
                .severity(SupportCase.CaseSeverity.MEDIUM)
                .summary("Test case")
                .correlationId("test-123")
                .build();
    }

    // ========== Least-Loaded Selection Tests ==========

    @Test
    void testLeastLoadedSelection() {
        // Given: 3 agents with different loads
        Agent alice = createAgent(1L, "Alice", "alice@test.com", 5);
        Agent bob = createAgent(2L, "Bob", "bob@test.com", 3);
        Agent carol = createAgent(3L, "Carol", "carol@test.com", 8);

        List<Agent> agents = List.of(alice, bob, carol);

        // When
        Agent selected = assignmentService.selectBestAgent(testCase, agents);

        // Then - Bob has least load (3)
        assertEquals(bob, selected);
    }

    @Test
    void testStableTieBreaking() {
        // Given: 2 agents with same load
        Agent alice = createAgent(1L, "Alice", "alice@test.com", 5);
        Agent bob = createAgent(2L, "Bob", "bob@test.com", 5);

        // When - pass in reverse order to test stable sorting
        Agent selected = assignmentService.selectBestAgent(testCase, List.of(bob, alice));

        // Then - Alice wins (lower ID)
        assertEquals(alice, selected);
    }

    @Test
    void testRespectsMaxActiveCases() {
        // Given: agents at different capacity levels
        Agent alice = createAgent(1L, "Alice", "alice@test.com", 10, 10); // At max
        Agent bob = createAgent(2L, "Bob", "bob@test.com", 9, 10); // Under max

        when(agentRepository.findByActiveTrue()).thenReturn(List.of(alice, bob));
        when(caseRepository.countByAssigneeIdAndStateIn(1L, anyList())).thenReturn(10L);
        when(caseRepository.countByAssigneeIdAndStateIn(2L, anyList())).thenReturn(9L);

        // When
        List<Agent> available = assignmentService.getAvailableAgents();

        // Then - only Bob is available
        assertEquals(1, available.size());
        assertEquals(bob, available.get(0));
    }

    // ========== Severity-Aware Routing Tests ==========

    @Test
    void testHighSeverityRoutesToSkilledAgent() throws Exception {
        // Given: HIGH severity case
        testCase.setSeverity(SupportCase.CaseSeverity.HIGH);

        Agent alice = createAgentWithSkill(1L, "Alice", "alice@test.com", 5, "[\"high_severity\"]");
        Agent bob = createAgent(2L, "Bob", "bob@test.com", 3); // Less loaded but no skill

        when(objectMapper.readValue("[\"high_severity\"]", List.class))
                .thenReturn(List.of("high_severity"));

        // When
        Agent selected = assignmentService.selectBestAgent(testCase, List.of(alice, bob));

        // Then - Alice selected despite higher load (has skill)
        assertEquals(alice, selected);
    }

    @Test
    void testHighSeverityFallbackWhenNoSkilledAgents() {
        // Given: HIGH severity case, no skilled agents
        testCase.setSeverity(SupportCase.CaseSeverity.HIGH);

        Agent alice = createAgent(1L, "Alice", "alice@test.com", 5);
        Agent bob = createAgent(2L, "Bob", "bob@test.com", 3);

        // When
        Agent selected = assignmentService.selectBestAgent(testCase, List.of(alice, bob));

        // Then - Falls back to least-loaded (Bob)
        assertEquals(bob, selected);
    }

    // ========== Auto-Assignment Tests ==========

    @Test
    void testAutoAssignSuccess() {
        // Given
        Agent bob = createAgent(2L, "Bob", "bob@test.com", 3);

        when(caseRepository.findById(1L)).thenReturn(Optional.of(testCase));
        when(agentRepository.findByActiveTrue()).thenReturn(List.of(bob));
        when(caseRepository.countByAssigneeIdAndStateIn(anyLong(), anyList())).thenReturn(3L);
        when(caseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // When
        AssignmentResult result = assignmentService.autoAssign(1L, "corr-123");

        // Then
        assertTrue(result.isSuccess());
        assertEquals(bob, result.getAssignedAgent());
        assertTrue(result.getReason().contains("least-loaded"));
        assertEquals("v1", result.getPolicyVersion());

        // Verify assignment event created
        verify(assignmentEventRepository).save(any(AssignmentEvent.class));
    }

    @Test
    void testAutoAssignAlreadyAssigned() {
        // Given: case already has assignee
        Agent alice = createAgent(1L, "Alice", "alice@test.com", 5);
        testCase.setAssignee(alice);

        when(caseRepository.findById(1L)).thenReturn(Optional.of(testCase));

        // When
        AssignmentResult result = assignmentService.autoAssign(1L, "corr-123");

        // Then
        assertFalse(result.isSuccess());
        assertTrue(result.getReason().contains("already assigned"));
        verify(caseRepository, never()).save(any());
    }

    @Test
    void testAutoAssignNoAgentsAvailable() {
        // Given: no agents available
        when(caseRepository.findById(1L)).thenReturn(Optional.of(testCase));
        when(agentRepository.findByActiveTrue()).thenReturn(List.of());

        // When
        AssignmentResult result = assignmentService.autoAssign(1L, "corr-123");

        // Then
        assertFalse(result.isSuccess());
        assertTrue(result.getReason().contains("No agents available"));
    }

    // ========== Manual Assignment Tests ==========

    @Test
    void testManualAssignSuccess() {
        // Given
        Agent bob = createAgent(2L, "Bob", "bob@test.com", 3);

        when(caseRepository.findById(1L)).thenReturn(Optional.of(testCase));
        when(agentRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(caseRepository.countByAssigneeIdAndStateIn(anyLong(), anyList())).thenReturn(3L);
        when(caseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // When
        AssignmentResult result = assignmentService.manualAssign(1L, 2L, "john.doe", "corr-456");

        // Then
        assertTrue(result.isSuccess());
        assertEquals(bob, result.getAssignedAgent());

        // Verify assignment event with correct actor
        ArgumentCaptor<AssignmentEvent> eventCaptor = ArgumentCaptor.forClass(AssignmentEvent.class);
        verify(assignmentEventRepository).save(eventCaptor.capture());
        assertEquals("john.doe", eventCaptor.getValue().getActor());
        assertEquals("corr-456", eventCaptor.getValue().getCorrelationId());
    }

    @Test
    void testManualAssignInactiveAgent() {
        // Given: inactive agent
        Agent inactive = createAgent(2L, "Inactive", "inactive@test.com", 0);
        inactive.setActive(false);

        when(caseRepository.findById(1L)).thenReturn(Optional.of(testCase));
        when(agentRepository.findById(2L)).thenReturn(Optional.of(inactive));

        // When
        AssignmentResult result = assignmentService.manualAssign(1L, 2L, "john.doe", "corr-456");

        // Then
        assertFalse(result.isSuccess());
        assertTrue(result.getReason().contains("inactive"));
    }

    // ========== Helper Methods ==========

    private Agent createAgent(Long id, String name, String email, int currentLoad) {
        return createAgent(id, name, email, currentLoad, 10);
    }

    private Agent createAgent(Long id, String name, String email, int currentLoad, int maxCases) {
        Agent agent = Agent.builder()
                .id(id)
                .name(name)
                .email(email)
                .active(true)
                .maxActiveCases(maxCases)
                .build();
        agent.setCurrentActiveCount(currentLoad);
        return agent;
    }

    private Agent createAgentWithSkill(Long id, String name, String email, int currentLoad, String skills) {
        Agent agent = createAgent(id, name, email, currentLoad);
        agent.setSkills(skills);
        return agent;
    }
}
