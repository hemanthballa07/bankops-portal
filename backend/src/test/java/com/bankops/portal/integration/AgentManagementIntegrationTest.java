package com.bankops.portal.integration;

import com.bankops.portal.entity.Agent;
import com.bankops.portal.repository.AgentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AgentManagementIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AgentRepository agentRepository;

    @BeforeEach
    void setUp() {
        agentRepository.deleteAll();
        agentRepository.save(Agent.builder().name("Ada Existing").email("ada@bank.test")
                .active(true).maxActiveCases(5).build());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_returnsAgentsWithLoad() throws Exception {
        mockMvc.perform(get("/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("ada@bank.test"))
                .andExpect(jsonPath("$[0].currentActiveCount").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_thenDuplicateEmailRejected() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Grace Hopper", "email", "grace@bank.test", "maxActiveCases", 8));
        mockMvc.perform(post("/agents").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("grace@bank.test"))
                .andExpect(jsonPath("$.active").value(true));
        // duplicate email -> 400
        mockMvc.perform(post("/agents").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_changesMaxCasesAndActive() throws Exception {
        Long id = agentRepository.findByEmail("ada@bank.test").getId();
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Ada Lovelace", "maxActiveCases", 12, "active", false));
        mockMvc.perform(put("/agents/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.maxActiveCases").value(12))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void patchActive_togglesAgent() throws Exception {
        Long id = agentRepository.findByEmail("ada@bank.test").getId();
        mockMvc.perform(patch("/agents/{id}/active", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @WithMockUser(roles = "USER")
    void nonAdmin_isForbidden() throws Exception {
        mockMvc.perform(get("/agents")).andExpect(status().isForbidden());
    }
}
