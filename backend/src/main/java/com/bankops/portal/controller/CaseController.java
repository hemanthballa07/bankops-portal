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

    @PutMapping("/{id}/assign")
    public ResponseEntity<SupportCaseDto> assignCase(
            @PathVariable Long id,
            @Valid @RequestBody AssignCaseRequest request) {
        SupportCaseDto supportCase = caseService.assignCase(id, request);
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
    }
