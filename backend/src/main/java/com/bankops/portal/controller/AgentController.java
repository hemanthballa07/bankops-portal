package com.bankops.portal.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bankops.portal.dto.AgentDto;
import com.bankops.portal.dto.CreateAgentRequest;
import com.bankops.portal.dto.UpdateAgentRequest;
import com.bankops.portal.service.AgentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** ADMIN-only agent management (URL secured in SecurityConfig). */
@RestController
@RequestMapping("/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @GetMapping
    public List<AgentDto> list() {
        return agentService.listAgents();
    }

    @PostMapping
    public ResponseEntity<AgentDto> create(@Valid @RequestBody CreateAgentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(agentService.createAgent(req));
    }

    @PutMapping("/{id}")
    public AgentDto update(@PathVariable Long id, @Valid @RequestBody UpdateAgentRequest req) {
        return agentService.updateAgent(id, req);
    }

    @PatchMapping("/{id}/active")
    public AgentDto setActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return agentService.setActive(id, Boolean.TRUE.equals(body.get("active")));
    }
}
