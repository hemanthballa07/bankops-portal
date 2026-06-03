package com.bankops.portal.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SlaConfigIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_returnsThreePriorities() throws Exception {
        mockMvc.perform(get("/admin/sla-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void put_persistsOverride_andGetReflectsIt() throws Exception {
        mockMvc.perform(put("/admin/sla-config/P1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationSeconds\": 3600}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("P1"))
                .andExpect(jsonPath("$.durationSeconds").value(3600));

        // list() returns priorities in SlaPriority.values() order (P1, P2, P3), so $[0] is P1.
        mockMvc.perform(get("/admin/sla-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].priority").value("P1"))
                .andExpect(jsonPath("$[0].durationSeconds").value(3600));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void put_invalidDuration_returns400() throws Exception {
        mockMvc.perform(put("/admin/sla-config/P1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationSeconds\": 0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "SUPPORT")
    void support_cannotAccess_returns403() throws Exception {
        mockMvc.perform(get("/admin/sla-config")).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_returns4xx() throws Exception {
        mockMvc.perform(get("/admin/sla-config")).andExpect(status().is4xxClientError());
    }
}
