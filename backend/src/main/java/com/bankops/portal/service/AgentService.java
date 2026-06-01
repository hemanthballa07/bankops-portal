package com.bankops.portal.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.bankops.portal.dto.AgentDto;
import com.bankops.portal.dto.CreateAgentRequest;
import com.bankops.portal.dto.UpdateAgentRequest;
import com.bankops.portal.entity.Agent;
import com.bankops.portal.repository.AgentRepository;
import com.bankops.portal.repository.SupportCaseRepository;
import com.bankops.portal.statemachine.CaseState;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/** Admin CRUD over support agents, with live per-agent active-case load. */
@Service
@RequiredArgsConstructor
public class AgentService {

    private static final List<CaseState> ACTIVE_STATES = List.of(CaseState.NEW, CaseState.IN_PROGRESS);

    private final AgentRepository agentRepository;
    private final SupportCaseRepository supportCaseRepository;
    private final ObjectMapper objectMapper;

    public List<AgentDto> listAgents() {
        return agentRepository.findAll().stream().map(this::toDto).toList();
    }

    public AgentDto createAgent(CreateAgentRequest req) {
        if (agentRepository.findByEmail(req.getEmail()) != null) {
            throw new IllegalArgumentException("An agent with email " + req.getEmail() + " already exists");
        }
        Agent agent = Agent.builder()
                .name(req.getName())
                .email(req.getEmail())
                .active(true)
                .maxActiveCases(req.getMaxActiveCases() != null ? req.getMaxActiveCases() : 10)
                .skills(writeSkills(req.getSkills()))
                .build();
        return toDto(agentRepository.save(agent));
    }

    public AgentDto updateAgent(Long id, UpdateAgentRequest req) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + id));
        agent.setName(req.getName());
        if (req.getMaxActiveCases() != null) {
            agent.setMaxActiveCases(req.getMaxActiveCases());
        }
        if (req.getActive() != null) {
            agent.setActive(req.getActive());
        }
        agent.setSkills(writeSkills(req.getSkills()));
        return toDto(agentRepository.save(agent));
    }

    public AgentDto setActive(Long id, boolean active) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + id));
        agent.setActive(active);
        return toDto(agentRepository.save(agent));
    }

    private AgentDto toDto(Agent a) {
        return AgentDto.builder()
                .id(a.getId())
                .name(a.getName())
                .email(a.getEmail())
                .active(a.getActive())
                .maxActiveCases(a.getMaxActiveCases())
                .currentActiveCount((int) supportCaseRepository
                        .countByAssigneeIdAndStateIn(a.getId(), ACTIVE_STATES))
                .skills(parseSkills(a.getSkills()))
                .build();
    }

    private List<String> parseSkills(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeSkills(List<String> skills) {
        if (skills == null || skills.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(skills);
        } catch (Exception e) {
            return null;
        }
    }
}
