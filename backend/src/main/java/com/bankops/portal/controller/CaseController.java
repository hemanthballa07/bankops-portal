package com.bankops.portal.controller;

import com.bankops.portal.dto.*;
import com.bankops.portal.entity.SupportCase;
import com.bankops.portal.service.CaseService;
import com.bankops.portal.statemachine.CaseState;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cases")
@RequiredArgsConstructor
public class CaseController {

    private final CaseService caseService;
    private final com.bankops.portal.assignment.AssignmentService assignmentService;

    @PostMapping
    public ResponseEntity<SupportCaseDto> createCase(@Valid @RequestBody CreateCaseRequest request) {
        SupportCaseDto supportCase = caseService.createCase(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(supportCase);
    }

    @GetMapping
    public ResponseEntity<List<SupportCaseDto>> getCases(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String severity) {

        CaseState caseState = null;
        if (state != null) {
            try {
                caseState = CaseState.valueOf(state.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Invalid state, will be handled by service
            }
        }

        SupportCase.CaseSeverity caseSeverity = null;
        if (severity != null) {
            try {
                caseSeverity = SupportCase.CaseSeverity.valueOf(severity.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Invalid severity, will be handled by service
            }
        }

        List<SupportCaseDto> cases = caseService.getCases(caseState, caseSeverity);
        return ResponseEntity.ok(cases);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SupportCaseDto> updateCaseStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCaseRequest request) {
        SupportCaseDto supportCase = caseService.updateCaseStatus(id, request);
        return ResponseEntity.ok(supportCase);
    }


    @PostMapping("/{id}/notes")
    public ResponseEntity<CaseNoteDto> addCaseNote(
            @PathVariable Long id,
            @Valid @RequestBody AddCaseNoteRequest request,
            Authentication authentication) {
        String author = authentication != null ? authentication.getName() : "SYSTEM";
        CaseNoteDto note = caseService.addCaseNote(id, request, author);
        return ResponseEntity.status(HttpStatus.CREATED).body(note);
    }

    @PostMapping("/{id}/transactions/{transactionId}")
    public ResponseEntity<SupportCaseDto> linkTransaction(
            @PathVariable Long id,
            @PathVariable Long transactionId) {
        SupportCaseDto supportCase = caseService.linkTransaction(id, transactionId);
        return ResponseEntity.ok(supportCase);
    }

    @PutMapping("/{id}/resolve")
    public ResponseEntity<SupportCaseDto> resolveCase(
            @PathVariable Long id,
            @Valid @RequestBody ResolveCaseRequest request) {
        SupportCaseDto supportCase = caseService.resolveCase(id, request);
        return ResponseEntity.ok(supportCase);
    }

    @GetMapping("/{id}/transitions")
    public ResponseEntity<CaseTransitionsDto> getAllowedTransitions(@PathVariable Long id) {
        CaseTransitionsDto transitions = caseService.getAllowedTransitions(id);
        return ResponseEntity.ok(transitions);
    }

    @GetMapping("/kpis")
    public ResponseEntity<com.bankops.portal.dto.CaseKpiDto> getKpis() {
        com.bankops.portal.dto.CaseKpiDto kpis = caseService.getKpis();
        return ResponseEntity.ok(kpis);
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<com.bankops.portal.dto.AssignmentResultDto> assignCase(
            @PathVariable Long id,
            @RequestBody com.bankops.portal.dto.AssignCaseRequest request) {
        com.bankops.portal.assignment.AssignmentResult result = assignmentService.manualAssign(
                id, request.getAgentId(), "USER", java.util.UUID.randomUUID().toString());
        return ResponseEntity.ok(toAssignmentResultDto(result));
    }

    @PostMapping("/{id}/auto-assign")
    public ResponseEntity<com.bankops.portal.dto.AssignmentResultDto> autoAssignCase(@PathVariable Long id) {
        com.bankops.portal.assignment.AssignmentResult result = assignmentService.autoAssign(
                id, java.util.UUID.randomUUID().toString());
        return ResponseEntity.ok(toAssignmentResultDto(result));
    }

    @GetMapping("/agents/available")
    public ResponseEntity<java.util.List<com.bankops.portal.dto.AgentDto>> getAvailableAgents() {
        java.util.List<com.bankops.portal.entity.Agent> agents = assignmentService.getAvailableAgents();
        return ResponseEntity.ok(agents.stream().map(this::toAgentDto).collect(java.util.stream.Collectors.toList()));
    }

    private com.bankops.portal.dto.AssignmentResultDto toAssignmentResultDto(
            com.bankops.portal.assignment.AssignmentResult result) {
        return com.bankops.portal.dto.AssignmentResultDto.builder()
                .success(result.isSuccess())
                .reason(result.getReason())
                .assignedAgent(result.getAssignedAgent() != null ? toAgentDto(result.getAssignedAgent()) : null)
                .policyVersion(result.getPolicyVersion())
                .build();
    }

    private com.bankops.portal.dto.AgentDto toAgentDto(com.bankops.portal.entity.Agent agent) {
        java.util.List<String> skills = null;
        if (agent.getSkills() != null && !agent.getSkills().isEmpty()) {
            try {
                skills = new com.fasterxml.jackson.databind.ObjectMapper().readValue(agent.getSkills(),
                        java.util.List.class);
            } catch (Exception e) {
                skills = java.util.Collections.emptyList();
            }
        }
        return com.bankops.portal.dto.AgentDto.builder()
                .id(agent.getId())
                .name(agent.getName())
                .email(agent.getEmail())
                .active(agent.getActive())
                .maxActiveCases(agent.getMaxActiveCases())
                .currentActiveCount(agent.getCurrentActiveCount())
                .skills(skills)
                .build();
    }
}
