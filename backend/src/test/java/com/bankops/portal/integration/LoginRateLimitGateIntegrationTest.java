package com.bankops.portal.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fluxguard.grpc.ratelimit.v1.CheckLimitRequest;
import com.fluxguard.grpc.ratelimit.v1.CheckLimitResponse;
import com.fluxguard.grpc.ratelimit.v1.Decision;
import com.fluxguard.grpc.ratelimit.v1.RateLimitGrpc;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;

/**
 * End-to-end integration test for the fluxguard LOGIN rate-limit gate on the
 * {@code /whoami} auth path. Replaces the real fluxguard gRPC channel with an in-process
 * server that always returns DECISION_DENY, then exercises {@code GET /api/whoami} via
 * MockMvc to assert the request fails fast with HTTP 429 — the {@code WhoamiRateLimitFilter}
 * runs before HTTP Basic auth, so no credential check happens.
 */
@SpringBootTest(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "bankops.fluxguard.enabled=true",
        "bankops.fluxguard.deadline=PT5S"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class LoginRateLimitGateIntegrationTest {

    private static final String INPROC_SERVER_NAME = "login-ratelimit-gate-integration-test";
    private static Server inProcessServer;

    @TestConfiguration
    static class FluxguardInProcessConfig {

        @Bean
        public DenyingRateLimitService denyingLoginRateLimitService() {
            return new DenyingRateLimitService();
        }

        @Bean
        public Server inProcessFluxguardServer(DenyingRateLimitService service) throws Exception {
            if (inProcessServer == null) {
                inProcessServer = InProcessServerBuilder
                        .forName(INPROC_SERVER_NAME)
                        .directExecutor()
                        .addService(service)
                        .build()
                        .start();
            }
            return inProcessServer;
        }

        @Bean
        @Primary
        public ManagedChannel fluxguardManagedChannel(Server inProcessFluxguardServer) {
            return InProcessChannelBuilder.forName(INPROC_SERVER_NAME)
                    .directExecutor()
                    .usePlaintext()
                    .build();
        }
    }

    @AfterAll
    static void stopServer() {
        if (inProcessServer != null) {
            inProcessServer.shutdownNow();
            inProcessServer = null;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deniedLogin_Returns429() throws Exception {
        mockMvc.perform(get("/api/whoami")
                        .contextPath("/api")
                        .servletPath("/whoami"))
                .andExpect(status().isTooManyRequests());
    }

    /** In-process gRPC service that always denies with a 2500ms retry-after. */
    static class DenyingRateLimitService extends RateLimitGrpc.RateLimitImplBase {
        @Override
        public void checkLimit(CheckLimitRequest request,
                StreamObserver<CheckLimitResponse> responseObserver) {
            responseObserver.onNext(CheckLimitResponse.newBuilder()
                    .setDecision(Decision.DECISION_DENY)
                    .setRetryAfterMs(2500L)
                    .setPolicyApplied("login")
                    .build());
            responseObserver.onCompleted();
        }
    }
}
