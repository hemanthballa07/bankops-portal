package com.bankops.portal.timeline;

import com.bankops.portal.dto.CaseSnapshotDto;
import com.bankops.portal.dto.CaseTimelineEventDto;
import com.bankops.portal.entity.*;
import com.bankops.portal.repository.*;
import com.bankops.portal.sla.SlaStatus;
import com.bankops.portal.statemachine.CaseState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for aggregating timeline events and reconstructing case state at any
 * point in time.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TimelineService {

    private final StateTransitionEventRepository stateTransitionRepository;
    private final com.bankops.portal.repository.SlaStatusChangeEventRepository slaEventRepository;
    private final AssignmentEventRepository assignmentEventRepository;
    private final SupportCaseRepository caseRepository;
    private final AgentRepository agentRepository;

    /**
     * Get unified timeline for a case with optional filtering and pagination.
     */
    @Transactional(readOnly = true)
    public List<CaseTimelineEventDto> getTimeline(Long caseId, TimelineFilter filter) {
        log.debug("Fetching timeline for case {} with filter: {}", caseId, filter);

        // 1. Fetch events from all sources
        List<StateTransitionEvent> stateEvents = stateTransitionRepository.findByCaseIdOrderByTimestampDesc(caseId);
        List<com.bankops.portal.entity.SlaStatusChangeEvent> slaEvents = slaEventRepository
                .findByCaseIdOrderByTimestampDesc(caseId);
        List<AssignmentEvent> assignmentEvents = assignmentEventRepository.findByCaseIdOrderByTimestampDesc(caseId);

        log.debug("Found {} state events, {} SLA events, {} assignment events",
                stateEvents.size(), slaEvents.size(), assignmentEvents.size());

        // 2. Convert to unified DTOs
        List<CaseTimelineEventDto> timeline = new ArrayList<>();
        timeline.addAll(convertStateEvents(stateEvents));
        timeline.addAll(convertSlaEvents(slaEvents));
        timeline.addAll(convertAssignmentEvents(assignmentEvents));

        // 3. Sort by timestamp descending, stable by ID
        timeline.sort(Comparator
                .comparing(CaseTimelineEventDto::getTimestamp).reversed()
                .thenComparing(CaseTimelineEventDto::getId));

        // 4. Apply filters
        if (filter.getEventType() != null) {
            timeline = timeline.stream()
                    .filter(e -> e.getEventType() == filter.getEventType())
                    .collect(Collectors.toList());
        }

        if (filter.getFromTimestamp() != null) {
            timeline = timeline.stream()
                    .filter(e -> e.getTimestamp().isAfter(filter.getFromTimestamp()))
                    .collect(Collectors.toList());
        }

        if (filter.getToTimestamp() != null) {
            timeline = timeline.stream()
                    .filter(e -> e.getTimestamp().isBefore(filter.getToTimestamp()))
                    .collect(Collectors.toList());
        }

        // 5. Paginate
        int start = filter.getPage() * filter.getPageSize();
        int end = Math.min(start + filter.getPageSize(), timeline.size());

        if (start >= timeline.size()) {
            return Collections.emptyList();
        }

        return timeline.subList(start, end);
    }

    /**
     * Reconstruct case state at a specific point in time.
     */
    @Transactional(readOnly = true)
    public CaseSnapshotDto replayAt(Long caseId, LocalDateTime timestamp) {
        log.debug("Replaying case {} at timestamp {}", caseId, timestamp);

        SupportCase currentCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        // Get all events up to timestamp, sorted chronologically (ascending)
        TimelineFilter filter = TimelineFilter.builder()
                .toTimestamp(timestamp)
                .pageSize(Integer.MAX_VALUE)
                .build();

        List<CaseTimelineEventDto> events = getTimeline(caseId, filter);
        Collections.reverse(events); // Now ascending

        // Start with initial state
        CaseSnapshotDto snapshot = CaseSnapshotDto.builder()
                .snapshotAt(timestamp)
                .state(CaseState.NEW.name())
                .createdAt(currentCase.getCreatedAt())
                .severity(currentCase.getSeverity() != null ? currentCase.getSeverity().name() : null)
                .build();

        // Apply events in chronological order
        for (CaseTimelineEventDto event : events) {
            applyEventToSnapshot(snapshot, event);
        }

        log.debug("Replay complete: state={}, assignee={}, slaStatus={}",
                snapshot.getState(), snapshot.getAssigneeName(), snapshot.getSlaStatus());

        return snapshot;
    }

    // ========== Event Conversion Methods ==========

    private List<CaseTimelineEventDto> convertStateEvents(List<StateTransitionEvent> events) {
        return events.stream()
                .map(this::convertStateEvent)
                .collect(Collectors.toList());
    }

    private CaseTimelineEventDto convertStateEvent(StateTransitionEvent event) {
        Map<String, Object> details = new HashMap<>();
        details.put("previousState", event.getFromState());
        details.put("newState", event.getToState());
        details.put("transitionType", event.getTransitionType());
        if (event.getReason() != null) {
            details.put("reason", event.getReason());
        }

        return CaseTimelineEventDto.builder()
                .id(event.getId())
                .timestamp(event.getTimestamp())
                .eventType(CaseTimelineEventDto.EventType.STATE_CHANGE)
                .summary(String.format("State changed from %s to %s",
                        event.getFromState(), event.getToState()))
                .details(details)
                .actor(event.getActor())
                .correlationId(event.getCorrelationId())
                .build();
    }

    private List<CaseTimelineEventDto> convertSlaEvents(List<com.bankops.portal.entity.SlaStatusChangeEvent> events) {
        return events.stream()
                .map(this::convertSlaEvent)
                .collect(Collectors.toList());
    }

    private CaseTimelineEventDto convertSlaEvent(com.bankops.portal.entity.SlaStatusChangeEvent event) {
        Map<String, Object> details = new HashMap<>();
        details.put("previousStatus", event.getPreviousStatus());
        details.put("newStatus", event.getNewStatus());

        String summary = String.format("SLA status changed to %s", event.getNewStatus());
        if (event.getNewStatus() != null && event.getNewStatus().equals(SlaStatus.BREACHED.name())) {
            summary = "⚠️ SLA BREACHED";
        } else if (event.getNewStatus() != null && event.getNewStatus().equals(SlaStatus.AT_RISK.name())) {
            summary = "⚠️ SLA at risk";
        }

        return CaseTimelineEventDto.builder()
                .id(event.getId())
                .timestamp(event.getTimestamp())
                .eventType(CaseTimelineEventDto.EventType.SLA_CHANGE)
                .summary(summary)
                .details(details)
                .actor(event.getActor())
                .correlationId(event.getCorrelationId())
                .build();
    }

    private List<CaseTimelineEventDto> convertAssignmentEvents(List<AssignmentEvent> events) {
        return events.stream()
                .map(this::convertAssignmentEvent)
                .collect(Collectors.toList());
    }

    private CaseTimelineEventDto convertAssignmentEvent(AssignmentEvent event) {
        Map<String, Object> details = new HashMap<>();
        details.put("previousAssigneeId", event.getPreviousAssigneeId());
        details.put("newAssigneeId", event.getNewAssigneeId());
        details.put("decisionReason", event.getDecisionReason());

        // Fetch agent name
        String agentName = "Unassigned";
        if (event.getNewAssigneeId() != null) {
            agentName = agentRepository.findById(event.getNewAssigneeId())
                    .map(Agent::getName)
                    .orElse("Unknown Agent");
        }
        details.put("newAssigneeName", agentName);

        String summary = event.getNewAssigneeId() != null
                ? String.format("Assigned to %s", agentName)
                : "Unassigned";

        return CaseTimelineEventDto.builder()
                .id(event.getId())
                .timestamp(event.getTimestamp())
                .eventType(CaseTimelineEventDto.EventType.ASSIGNMENT)
                .summary(summary)
                .details(details)
                .actor(event.getActor())
                .correlationId(event.getCorrelationId())
                .build();
    }

    // ========== Replay Logic ==========

    private void applyEventToSnapshot(CaseSnapshotDto snapshot, CaseTimelineEventDto event) {
        switch (event.getEventType()) {
            case STATE_CHANGE:
                String newState = (String) event.getDetails().get("newState");
                if (newState != null) {
                    snapshot.setState(newState);
                }
                break;

            case SLA_CHANGE:
                String newStatus = (String) event.getDetails().get("newStatus");
                if (newStatus != null) {
                    snapshot.setSlaStatus(newStatus);
                }
                break;

            case ASSIGNMENT:
                Long newAssigneeId = (Long) event.getDetails().get("newAssigneeId");
                String newAssigneeName = (String) event.getDetails().get("newAssigneeName");
                snapshot.setAssigneeId(newAssigneeId);
                snapshot.setAssigneeName(newAssigneeName);
                break;
        }
    }
}
