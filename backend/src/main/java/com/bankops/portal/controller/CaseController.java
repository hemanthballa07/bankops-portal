package com.bankops.portal.controller;

import com.bankops.portal.dto.CreateCaseRequest;
import com.bankops.portal.dto.SupportCaseDto;
import com.bankops.portal.dto.UpdateCaseRequest;
import com.bankops.portal.entity.SupportCase;
import com.bankops.portal.service.CaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity) {
        
        SupportCase.CaseStatus caseStatus = null;
        if (status != null) {
            try {
                caseStatus = SupportCase.CaseStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Invalid status, will be handled by service
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
        
        List<SupportCaseDto> cases = caseService.getCases(caseStatus, caseSeverity);
        return ResponseEntity.ok(cases);
    }
    
    @PatchMapping("/{id}")
    public ResponseEntity<SupportCaseDto> updateCaseStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCaseRequest request) {
        SupportCaseDto supportCase = caseService.updateCaseStatus(id, request);
        return ResponseEntity.ok(supportCase);
    }
}

