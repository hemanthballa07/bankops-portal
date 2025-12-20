package com.bankops.portal.controller;

import com.bankops.portal.dto.IncidentResponse;
import com.bankops.portal.service.IncidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/incidents")
@RequiredArgsConstructor
public class IncidentController {
    
    private final IncidentService incidentService;
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", 
        Pattern.CASE_INSENSITIVE
    );
    
    @GetMapping("/{correlationId}")
    public ResponseEntity<IncidentResponse> getIncidentByCorrelationId(@PathVariable String correlationId) {
        // Validate UUID format
        if (correlationId == null || !UUID_PATTERN.matcher(correlationId).matches()) {
            throw new IllegalArgumentException("Invalid correlation ID format. Expected UUID.");
        }
        
        IncidentResponse incident = incidentService.getIncidentByCorrelationId(correlationId);
        return ResponseEntity.ok(incident);
    }
}

