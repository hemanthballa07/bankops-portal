package com.bankops.portal.timeline;

import com.bankops.portal.dto.CaseSnapshotDto;
import com.bankops.portal.dto.CaseTimelineEventDto;
import com.bankops.portal.entity.*;
import com.bankops.portal.repository.*;
import com.bankops.portal.sla.SlaStatus;
import com.bankops.portal.statemachine.CaseState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TimelineService with deterministic timestamps.
 */
@ExtendWith(MockitoExtension.class)
class TimelineServiceTest {

    @Mock
    private StateTransitionEventRepository stateTransitionRepository;

    @Mock
    private com.bankops.portal.repository.SlaStatusChangeEventRepository slaEventRepository;

    @Mock
    private AssignmentEventRepository assignmentEventRepository;

    @Mock
    private SupportCaseRepository caseRepository;

    @Mock
    private AgentRepository agentRepository;

    @InjectMocks
    private TimelineService timelineService;

    private static final Long CASE_ID = 1L;
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 1, 2, 10, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 1, 2, 11, 0);
    private static final LocalDateTime T3 = LocalDateTime.of(2026, 1, 2, 12, 0);

    private SupportCase testCase;

    @BeforeEach
    void setUp() {
        testCase = SupportCase.builder()
                .id(CASE_ID)
                .state(CaseState.IN_PROGRESS)
                .severity(SupportCase.CaseSeverity.HIGH)
                .createdAt(T1)
                .build();
    }

    // ========== Event Aggregation Tests ==========

    @Test
    void testMergesEventsFromAllSources() {
        // Given: events from all 3 sources
        StateTransitionEvent stateEvent = createStateEvent(1L, T1, "NEW", "ASSIGNED");
        com.bankops.portal.entity.SlaStatusChangeEvent slaEvent = createSlaEvent(2L, T2, "ON_TRACK", "AT_RISK");
        AssignmentEvent assignEvent = createAssignmentEvent(3L, T3, null, 1L);

        when(stateTransitionRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of(stateEvent));
        when(slaEventRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of(slaEvent));
        when(assignmentEventRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of(assignEvent));
        when(agentRepository.findById(1L))
                .thenReturn(Optional.of(createAgent(1L, "Alice")));

        // When
        List<CaseTimelineEventDto> timeline = timelineService.getTimeline(CASE_ID, emptyFilter());

        // Then - all 3 events present
        assertEquals(3, timeline.size());

        // Verify event types
        assertTrue(timeline.stream().anyMatch(e -> e.getEventType() == CaseTimelineEventDto.EventType.STATE_CHANGE));
        assertTrue(timeline.stream().anyMatch(e -> e.getEventType() == CaseTimelineEventDto.EventType.SLA_CHANGE));
        assertTrue(timeline.stream().anyMatch(e -> e.getEventType() == CaseTimelineEventDto.EventType.ASSIGNMENT));
    }

    @Test
    void testSortsChronologicallyDescending() {
        // Given: events with different timestamps
        StateTransitionEvent event1 = createStateEvent(1L, T1, "NEW", "ASSIGNED");
        StateTransitionEvent event2 = createStateEvent(2L, T3, "ASSIGNED", "IN_PROGRESS");
        StateTransitionEvent event3 = createStateEvent(3L, T2, "IN_PROGRESS", "RESOLVED");

        when(stateTransitionRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of(event1, event2, event3));
        when(slaEventRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of());
        when(assignmentEventRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of());

        // When
        List<CaseTimelineEventDto> timeline = timelineService.getTimeline(CASE_ID, emptyFilter());

        // Then - sorted by timestamp descending
        assertEquals(3, timeline.size());
        assertEquals(T3, timeline.get(0).getTimestamp()); // Most recent
        assertEquals(T2, timeline.get(1).getTimestamp());
        assertEquals(T1, timeline.get(2).getTimestamp());
    }

    @Test
    void testStableSortingForSameTimestamp() {
        // Given: events with same timestamp but different IDs
        StateTransitionEvent event1 = createStateEvent(1L, T1, "NEW", "ASSIGNED");
        StateTransitionEvent event2 = createStateEvent(2L, T1, "ASSIGNED", "IN_PROGRESS");

        when(stateTransitionRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of(event1, event2));
        when(slaEventRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of());
        when(assignmentEventRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of());

        // When
        List<CaseTimelineEventDto> timeline = timelineService.getTimeline(CASE_ID, emptyFilter());

        // Then - stable sort by ID
        assertEquals(2, timeline.size());
        assertEquals(1L, timeline.get(0).getId()); // Lower ID first for same timestamp
        assertEquals(2L, timeline.get(1).getId());
    }

    // ========== Filtering Tests ==========

    @Test
    void testFilterByEventType() {
        // Given: mixed event types
        StateTransitionEvent stateEvent = createStateEvent(1L, T1, "NEW", "ASSIGNED");
        com.bankops.portal.entity.SlaStatusChangeEvent slaEvent = createSlaEvent(2L, T2, "ON_TRACK", "AT_RISK");

        when(stateTransitionRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of(stateEvent));
        when(slaEventRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of(slaEvent));
        when(assignmentEventRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of());

        // When - filter for STATE_CHANGE only
        TimelineFilter filter = TimelineFilter.builder()
                .eventType(CaseTimelineEventDto.EventType.STATE_CHANGE)
                .build();
        List<CaseTimelineEventDto> timeline = timelineService.getTimeline(CASE_ID, filter);

        // Then - only state change events
        assertEquals(1, timeline.size());
        assertEquals(CaseTimelineEventDto.EventType.STATE_CHANGE, timeline.get(0).getEventType());
    }

    @Test
    void testFilterByTimestampRange() {
        // Given: events across time range
        StateTransitionEvent event1 = createStateEvent(1L, T1, "NEW", "ASSIGNED");
        StateTransitionEvent event2 = createStateEvent(2L, T2, "ASSIGNED", "IN_PROGRESS");
        StateTransitionEvent event3 = createStateEvent(3L, T3, "IN_PROGRESS", "RESOLVED");

        when(stateTransitionRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of(event1, event2, event3));
        when(slaEventRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of());
        when(assignmentEventRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of());

        // When - filter from T1 to T2
        TimelineFilter filter = TimelineFilter.builder()
                .fromTimestamp(T1)
                .toTimestamp(T3)
                .build();
        List<CaseTimelineEventDto> timeline = timelineService.getTimeline(CASE_ID, filter);

        // Then - only events in range
        assertEquals(1, timeline.size());
        assertEquals(T2, timeline.get(0).getTimestamp());
    }

    // ========== Pagination Tests ==========

    @Test
    void testPaginationReturnsCorrectSlice() {
        // Given: 5 events
        List<StateTransitionEvent> events = List.of(
                createStateEvent(1L, T1, "NEW", "ASSIGNED"),
                createStateEvent(2L, T1.plusMinutes(10), "ASSIGNED", "IN_PROGRESS"),
                createStateEvent(3L, T1.plusMinutes(20), "IN_PROGRESS", "PENDING_CUSTOMER"),
                createStateEvent(4L, T1.plusMinutes(30), "PENDING_CUSTOMER", "IN_PROGRESS"),
                createStateEvent(5L, T1.plusMinutes(40), "IN_PROGRESS", "RESOLVED"));

        when(stateTransitionRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(events);
        when(slaEventRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of());
        when(assignmentEventRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of());

        // When - page 0, size 2
        TimelineFilter filter = TimelineFilter.builder()
                .page(0)
                .pageSize(2)
                .build();
        List<CaseTimelineEventDto> timeline = timelineService.getTimeline(CASE_ID, filter);

        // Then - first 2 events
        assertEquals(2, timeline.size());

        // When - page 1, size 2
        filter = TimelineFilter.builder()
                .page(1)
                .pageSize(2)
                .build();
        timeline = timelineService.getTimeline(CASE_ID, filter);

        // Then - next 2 events
        assertEquals(2, timeline.size());
    }

    // ========== Event Mapping Tests ==========

    @Test
    void testStateEventMappingContainsExpectedFields() {
        // Given
        StateTransitionEvent event = createStateEvent(1L, T1, "NEW", "ASSIGNED");
        event.setActor("john.doe");
        event.setCorrelationId("abc-123");
        event.setReason("User action");

        when(stateTransitionRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of(event));
        when(slaEventRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of());
        when(assignmentEventRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of());

        // When
        List<CaseTimelineEventDto> timeline = timelineService.getTimeline(CASE_ID, emptyFilter());

        // Then
        assertEquals(1, timeline.size());
        CaseTimelineEventDto dto = timeline.get(0);

        assertEquals("State changed from NEW to ASSIGNED", dto.getSummary());
        assertEquals("john.doe", dto.getActor());
        assertEquals("abc-123", dto.getCorrelationId());
        assertEquals("NEW", dto.getDetails().get("previousState"));
        assertEquals("ASSIGNED", dto.getDetails().get("newState"));
        assertEquals("User action", dto.getDetails().get("reason"));
    }

    @Test
    void testSlaEventMappingWithBreachWarning() {
        // Given
        com.bankops.portal.entity.SlaStatusChangeEvent event = createSlaEvent(1L, T1, "AT_RISK", "BREACHED");
        event.setActor("SYSTEM");
        event.setCorrelationId("xyz-789");

        when(stateTransitionRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of());
        when(slaEventRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of(event));
        when(assignmentEventRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of());

        // When
        List<CaseTimelineEventDto> timeline = timelineService.getTimeline(CASE_ID, emptyFilter());

        // Then
        assertEquals(1, timeline.size());
        CaseTimelineEventDto dto = timeline.get(0);

        assertEquals("⚠️ SLA BREACHED", dto.getSummary());
        assertEquals("SYSTEM", dto.getActor());
        assertEquals("AT_RISK", dto.getDetails().get("previousStatus"));
        assertEquals("BREACHED", dto.getDetails().get("newStatus"));
    }

    @Test
    void testAssignmentEventResolvesAgentName() {
        // Given
        AssignmentEvent event = createAssignmentEvent(1L, T1, null, 2L);
        event.setActor("SYSTEM");
        event.setDecisionReason("Auto-assigned: least-loaded");

        Agent agent = createAgent(2L, "Bob Smith");

        when(stateTransitionRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of());
        when(slaEventRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of());
        when(assignmentEventRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of(event));
        when(agentRepository.findById(2L))
                .thenReturn(Optional.of(agent));

        // When
        List<CaseTimelineEventDto> timeline = timelineService.getTimeline(CASE_ID, emptyFilter());

        // Then
        assertEquals(1, timeline.size());
        CaseTimelineEventDto dto = timeline.get(0);

        assertEquals("Assigned to Bob Smith", dto.getSummary());
        assertEquals("Bob Smith", dto.getDetails().get("newAssigneeName"));
        assertEquals(2L, dto.getDetails().get("newAssigneeId"));
    }

    // ========== Replay Tests ==========

    @Test
    void testReplayReconstructsStateCorrectly() {
        // Given: state change at T1, assignment at T2
        StateTransitionEvent stateEvent = createStateEvent(1L, T1, "NEW", "ASSIGNED");
        AssignmentEvent assignEvent = createAssignmentEvent(2L, T2, null, 1L);

        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(testCase));
        when(stateTransitionRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of(stateEvent));
        when(slaEventRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of());
        when(assignmentEventRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of(assignEvent));
        when(agentRepository.findById(1L))
                .thenReturn(Optional.of(createAgent(1L, "Alice")));

        // When - replay at T2 (after both events)
        CaseSnapshotDto snapshot = timelineService.replayAt(CASE_ID, T2);

        // Then
        assertEquals("ASSIGNED", snapshot.getState());
        assertEquals(1L, snapshot.getAssigneeId());
        assertEquals("Alice", snapshot.getAssigneeName());
    }

    @Test
    void testReplayBeforeAnyEvents() {
        // Given: events at T2
        StateTransitionEvent event = createStateEvent(1L, T2, "NEW", "ASSIGNED");

        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(testCase));
        when(stateTransitionRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of(event));
        when(slaEventRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of());
        when(assignmentEventRepository.findByCaseIdOrderByTimestampDesc(CASE_ID))
                .thenReturn(List.of());

        // When - replay at T1 (before event)
        CaseSnapshotDto snapshot = timelineService.replayAt(CASE_ID, T1);

        // Then - initial state
        assertEquals("NEW", snapshot.getState());
        assertNull(snapshot.getAssigneeId());
    }

    // ========== Helper Methods ==========

    private TimelineFilter emptyFilter() {
        return TimelineFilter.builder().build();
    }

    private StateTransitionEvent createStateEvent(Long id, LocalDateTime timestamp, String from, String to) {
        return StateTransitionEvent.builder()
                .id(id)
                .caseId(CASE_ID)
                .fromState(from)
                .toState(to)
                .timestamp(timestamp)
                .actor("SYSTEM")
                .correlationId("test-corr")
                .build();
    }

    private com.bankops.portal.entity.SlaStatusChangeEvent createSlaEvent(Long id, LocalDateTime timestamp,
            String prevStatus, String newStatus) {
        return com.bankops.portal.entity.SlaStatusChangeEvent.builder()
                .id(id)
                .caseId(CASE_ID)
                .previousStatus(prevStatus)
                .newStatus(newStatus)
                .timestamp(timestamp)
                .actor("SYSTEM")
                .correlationId("test-corr")
                .build();
    }

    private AssignmentEvent createAssignmentEvent(Long id, LocalDateTime timestamp,
            Long prevAssigneeId, Long newAssigneeId) {
        return AssignmentEvent.builder()
                .id(id)
                .caseId(CASE_ID)
                .previousAssigneeId(prevAssigneeId)
                .newAssigneeId(newAssigneeId)
                .timestamp(timestamp)
                .actor("SYSTEM")
                .correlationId("test-corr")
                .build();
    }

    private Agent createAgent(Long id, String name) {
        return Agent.builder()
                .id(id)
                .name(name)
                .email(name.toLowerCase().replace(" ", ".") + "@test.com")
                .build();
    }
}
