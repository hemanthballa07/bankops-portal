package com.bankops.portal.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bankops.portal.dto.SlaConfigDto;
import com.bankops.portal.sla.SlaConfigService;

import lombok.RequiredArgsConstructor;

/** ADMIN-only runtime SLA duration config. Served at /api/admin/sla-config. */
@RestController
@RequestMapping("/admin/sla-config")
@RequiredArgsConstructor
public class SlaConfigController {

    private final SlaConfigService slaConfigService;

    @GetMapping
    public List<SlaConfigDto> list() {
        return slaConfigService.list();
    }

    @PutMapping("/{priority}")
    public SlaConfigDto update(@PathVariable String priority, @RequestBody Map<String, Long> body) {
        Long seconds = body.get("durationSeconds");
        if (seconds == null) {
            throw new IllegalArgumentException("durationSeconds is required");
        }
        return slaConfigService.update(priority, seconds);
    }
}
