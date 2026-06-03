package com.bankops.portal.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bankops.portal.dto.MlRiskBandConfigDto;
import com.bankops.portal.service.MlRiskBandConfigService;

import lombok.RequiredArgsConstructor;

/**
 * Effective ML risk band thresholds. GET is readable by any authenticated user (the chip needs it);
 * PUT is ADMIN-only (enforced by a method-matcher in SecurityConfig). Served at /api/ml-risk-bands.
 */
@RestController
@RequestMapping("/ml-risk-bands")
@RequiredArgsConstructor
public class MlRiskBandController {

    private final MlRiskBandConfigService service;

    @GetMapping
    public MlRiskBandConfigDto get() {
        return service.getConfig();
    }

    @PutMapping
    public MlRiskBandConfigDto update(@RequestBody Map<String, Double> body) {
        Double med = body.get("medThreshold");
        Double high = body.get("highThreshold");
        if (med == null || high == null) {
            throw new IllegalArgumentException("medThreshold and highThreshold are required");
        }
        return service.update(med, high);
    }
}
