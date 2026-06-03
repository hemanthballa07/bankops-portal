package com.bankops.portal.integration;

import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.bankops.portal.repository.MlRiskBandConfigRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class MlRiskBandConfigIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private MlRiskBandConfigRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    @WithMockUser(roles = "USER")
    void get_returnsDefaults_onEmptyDb_forAnyAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/ml-risk-bands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medThreshold").value(closeTo(0.40, 1e-6)))
                .andExpect(jsonPath("$.highThreshold").value(closeTo(0.70, 1e-6)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void put_asAdmin_updatesAndGetReflectsIt() throws Exception {
        mockMvc.perform(put("/ml-risk-bands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"medThreshold\":0.35,\"highThreshold\":0.65}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medThreshold").value(closeTo(0.35, 1e-6)))
                .andExpect(jsonPath("$.highThreshold").value(closeTo(0.65, 1e-6)));

        mockMvc.perform(get("/ml-risk-bands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medThreshold").value(closeTo(0.35, 1e-6)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void put_invalidOrdering_returns400() throws Exception {
        mockMvc.perform(put("/ml-risk-bands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"medThreshold\":0.7,\"highThreshold\":0.4}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void put_asUser_returns403() throws Exception {
        mockMvc.perform(put("/ml-risk-bands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"medThreshold\":0.35,\"highThreshold\":0.65}"))
                .andExpect(status().isForbidden());
    }
}
