package com.bankops.portal.assignment;

import com.bankops.portal.entity.Agent;
import com.bankops.portal.entity.AssignmentEvent;
import com.bankops.portal.entity.SupportCase;
import com.bankops.portal.repository.AgentRepository;
import com.bankops.portal.repository.AssignmentEventRepository;
import com.bankops.portal.repository.SupportCaseRepository;
import com.bankops.portal.statemachine.CaseState;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for auto-assignment and manual assignment of cases to agents.
 * Implements least-loaded algorithm with severity-aware routing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssignmentService {

    private final AgentRepository agentRepository;
    private final SupportCaseRepository caseRepository;
    private final AssignmentEventRepository assignmentEventRepository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    private static final String POLICY_VERSION = "v1";
    private static final List<CaseState> ACTIVE_STATES = List.of(
            CaseState.NEW,
            CaseState.ASSIGNED,
            CaseState.IN_PROGRESS,
            CaseState.PENDING_CUSTOMER);

    /**
     * Auto-assign a case using least-loaded algorithm.
     * Returns result with assigned agent or failure reason.
     */
    @Transactional
    public AssignmentResult autoAssign(Long caseId, String correlationId) {
        SupportCase supportCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        // Check if already assigned
        if (supportCase.getAssignee() != null) {
            return AssignmentResult.builder()
                    .success(false)
                    .reason("Case already assigned to " + supportCase.getAssignee().getName())
                    .build();
        }

        // Check if case is in eligible state
        if (supportCase.getState() != CaseState.NEW) {
            return AssignmentResult.builder()
                    .success(false)
                    .reason("Case must be in NEW state for auto-assignment (current: " + supportCase.getState() + ")")
                    .build();
        }

        // Get available agents with current load
        List<Agent> availableAgents = getAvailableAgents();
        if (availableAgents.isEmpty()) {
            return AssignmentResult.builder()
                    .success(false)
                    .reason("No agents available (all at max capacity or inactive)")
                    .build();
        }

        // Select best agent using least-loaded algorithm
        Agent selectedAgent = selectBestAgent(supportCase, availableAgents);
        if (selectedAgent == null) {
            return AssignmentResult.builder()
                    .success(false)
                    .reason("No suitable agent found")
                    .build();
        }

        // Assign case
        supportCase.setAssignee(selectedAgent);
        supportCase.setAssignedAt(LocalDateTime.now(clock));
        supportCase = caseRepository.save(supportCase);

        // Build decision inputs for audit
        Map<String, Object> decisionInputs = new HashMap<>();
        decisionInputs.put("severity", supportCase.getSeverity().name());
        decisionInputs.put("slaStatus",
                supportCase.getSlaStatus() != null ? supportCase.getSlaStatus().name() : "NONE");
        decisionInputs.put("agentLoad", selectedAgent.getCurrentActiveCount());
        decisionInputs.put("agentMaxLoad", selectedAgent.getMaxActiveCases());

        String reason = String.format("Auto-assigned: least-loaded agent (%s with %d/%d active cases)",
                selectedAgent.getName(), selectedAgent.getCurrentActiveCount(), selectedAgent.getMaxActiveCases());

        // Record assignment event
        recordAssignmentEvent(supportCase, null, selectedAgent, "SYSTEM", reason,
                decisionInputs, correlationId);

        log.info("Auto-assigned case {} to agent {} (correlation: {})",
                caseId, selectedAgent.getName(), correlationId);

        return AssignmentResult.builder()
                .success(true)
                .assignedAgent(selectedAgent)
                .reason(reason)
                .policyVersion(POLICY_VERSION)
                .build();
    }

    /**
     * Manually assign a case to a specific agent.
     */
    @Transactional
    public AssignmentResult manualAssign(Long caseId, Long agentId, String actor, String correlationId) {
        SupportCase supportCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        // Check if agent is active
        if (!agent.getActive()) {
            return AssignmentResult.builder()
                    .success(false)
                    .reason("Agent " + agent.getName() + " is inactive")
                    .build();
        }

        // Check agent capacity (warning only, allow override)
        int currentLoad = getCurrentActiveCount(agent.getId());
        boolean overCapacity = currentLoad >= agent.getMaxActiveCases();

        Agent previousAssignee = supportCase.getAssignee();
        supportCase.setAssignee(agent);
        supportCase.setAssignedAt(LocalDateTime.now(clock));
        supportCase = caseRepository.save(supportCase);

        // Build decision inputs
        Map<String, Object> decisionInputs = new HashMap<>();
        decisionInputs.put("severity", supportCase.getSeverity().name());
        decisionInputs.put("agentLoad", currentLoad);
        decisionInputs.put("agentMaxLoad", agent.getMaxActiveCases());
        decisionInputs.put("overCapacity", overCapacity);

        String reason = String.format("Manual assignment by %s%s",
                actor, overCapacity ? " (agent over capacity)" : "");

        // Record assignment event
        recordAssignmentEvent(supportCase, previousAssignee, agent, actor, reason,
                decisionInputs, correlationId);

        log.info("Manually assigned case {} to agent {} by {} (correlation: {})",
                caseId, agent.getName(), actor, correlationId);

        return AssignmentResult.builder()
                .success(true)
                .assignedAgent(agent)
                .reason(reason)
                .policyVersion(POLICY_VERSION)
                .build();
    }

    /**
     * Get all available agents with their current active case counts.
     */
    public List<Agent> getAvailableAgents() {
        List<Agent> agents = agentRepository.findByActiveTrue();

        // Enrich with current active counts
        for (Agent agent : agents) {
            int activeCount = getCurrentActiveCount(agent.getId());
            agent.setCurrentActiveCount(activeCount);
        }

        // Filter agents under max capacity
        return agents.stream()
                .filter(a -> a.getCurrentActiveCount() < a.getMaxActiveCases())
                .collect(Collectors.toList());
    }

    /**
     * Select best agent using least-loaded algorithm with severity-aware routing.
     */
    public Agent selectBestAgent(SupportCase supportCase, List<Agent> availableAgents) {
        if (availableAgents.isEmpty()) {
            return null;
        }

        List<Agent> candidates = new ArrayList<>(availableAgents);

        // For HIGH severity, prefer agents with "high_severity" skill
        if (supportCase.getSeverity() == SupportCase.CaseSeverity.HIGH) {
            List<Agent> skilledAgents = candidates.stream()
                    .filter(a -> hasSkill(a, "high_severity"))
                    .collect(Collectors.toList());

            if (!skilledAgents.isEmpty()) {
                candidates = skilledAgents;
                log.debug("Filtered to {} agents with high_severity skill", candidates.size());
            }
        }

        // Sort by current load (ascending), then by ID (stable tie-breaking)
        candidates.sort(Comparator
                .comparingInt(Agent::getCurrentActiveCount)
                .thenComparingLong(Agent::getId));

        return candidates.get(0); // Return least-loaded
    }

    /**
     * Get current active case count for an agent.
     */
    private int getCurrentActiveCount(Long agentId) {
        return (int) caseRepository.countByAssigneeIdAndStateIn(agentId, ACTIVE_STATES);
    }

    /**
     * Check if agent has a specific skill.
     */
    private boolean hasSkill(Agent agent, String skill) {
        if (agent.getSkills() == null || agent.getSkills().isEmpty()) {
            return false;
        }

        try {
            List<String> skills = objectMapper.readValue(agent.getSkills(), List.class);
            return skills.contains(skill);
        } catch (Exception e) {
            log.warn("Failed to parse skills for agent {}: {}", agent.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Record assignment event for audit trail.
     */
    private void recordAssignmentEvent(SupportCase supportCase, Agent previousAssignee,
            Agent newAssignee, String actor, String reason,
            Map<String, Object> decisionInputs, String correlationId) {
        try {
            String inputsJson = objectMapper.writeValueAsString(decisionInputs);

            AssignmentEvent event = AssignmentEvent.builder()
                    .caseId(supportCase.getId())
                    .previousAssigneeId(previousAssignee != null ? previousAssignee.getId() : null)
                    .newAssigneeId(newAssignee.getId())
                    .decisionPolicyVersion(POLICY_VERSION)
                    .decisionReason(reason)
                    .decisionInputs(inputsJson)
                    .timestamp(LocalDateTime.now(clock))
                    .correlationId(correlationId)
                    .actor(actor)
                    .build();

            assignmentEventRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to record assignment event: {}", e.getMessage(), e);
            // Don't fail the assignment if audit logging fails
        }
    }
}
