package com.bankops.portal.controller;

import com.bankops.portal.dto.IncidentResponse;
import com.bankops.portal.service.IncidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/incidents")
@RequiredArgsConstructor
public class IncidentController {
    
    private final IncidentService incidentService;
    
    @GetMapping("/{correlationId}")
    public ResponseEntity<IncidentResponse> getIncidentByCorrelationId(@PathVariable String correlationId) {
        IncidentResponse incident = incidentService.getIncidentByCorrelationId(correlationId);
        return ResponseEntity.ok(incident);
    }
}

