package com.bankops.portal.integration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.bankops.portal.client.fluxguard.FluxguardRateLimitClient;
import com.fluxguard.grpc.ratelimit.v1.CheckLimitRequest;
import com.fluxguard.grpc.ratelimit.v1.CheckLimitResponse;
import com.fluxguard.grpc.ratelimit.v1.Decision;
import com.fluxguard.grpc.ratelimit.v1.RateLimitGrpc;
import com.fluxguard.grpc.ratelimit.v1.ReportLoginFailureRequest;
import com.fluxguard.grpc.ratelimit.v1.ReportLoginFailureResponse;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;

/**
 * Integration test for fluxguard LOGIN-failure reporting on the {@code /whoami} auth
 * path. fluxguard is enabled and backed by an in-process server that always ALLOWs the
 * rate-limit check (so the gate passes through to HTTP Basic auth). A
 * {@link FluxguardRateLimitClient} {@code @SpyBean} verifies that
 * {@code reportLoginFailure} is called exactly once on a bad-credential 401, and never
 * on a successful auth (200) or an anonymous request (401 with no credentials).
 */
@SpringBootTest(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "bankops.fluxguard.enabled=true",
        "bankops.fluxguard.deadline=PT5S"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class LoginFailureReportIntegrationTest {

    private static final String INPROC_SERVER_NAME = "login-failure-report-integration-test";
    private static Server inProcessServer;

    @TestConfiguration
    static class FluxguardInProcessConfig {

        @Bean
        public AllowingRateLimitService allowingLoginRateLimitService() {
            return new AllowingRateLimitService();
        }

        @Bean
        public Server inProcessFluxguardServer(AllowingRateLimitService service) throws Exception {
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

    @SpyBean
    private FluxguardRateLimitClient fluxguardRateLimitClient;

    @BeforeEach
    void resetSpy() {
        reset(fluxguardRateLimitClient);
    }

    @Test
    void wrongCredentials_reportsLoginFailureOnce() throws Exception {
        mockMvc.perform(get("/api/whoami")
                        .contextPath("/api")
                        .servletPath("/whoami")
                        .with(httpBasic("user", "wrong-password")))
                .andExpect(status().isUnauthorized());

        verify(fluxguardRateLimitClient, times(1)).reportLoginFailure(anyString(), anyString());
    }

    @Test
    void correctCredentials_returns200AndNeverReports() throws Exception {
        mockMvc.perform(get("/api/whoami")
                        .contextPath("/api")
                        .servletPath("/whoami")
                        .with(httpBasic("user", "password")))
                .andExpect(status().isOk());

        verify(fluxguardRateLimitClient, never()).reportLoginFailure(anyString(), anyString());
    }

    @Test
    void noCredentials_returns401AndNeverReports() throws Exception {
        mockMvc.perform(get("/api/whoami")
                        .contextPath("/api")
                        .servletPath("/whoami"))
                .andExpect(status().isUnauthorized());

        verify(fluxguardRateLimitClient, never()).reportLoginFailure(anyString(), anyString());
    }

    /** In-process gRPC service that always allows the rate-limit check. */
    static class AllowingRateLimitService extends RateLimitGrpc.RateLimitImplBase {
        @Override
        public void checkLimit(CheckLimitRequest request,
                StreamObserver<CheckLimitResponse> responseObserver) {
            responseObserver.onNext(CheckLimitResponse.newBuilder()
                    .setDecision(Decision.DECISION_ALLOW)
                    .setPolicyApplied("login")
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public void reportLoginFailure(ReportLoginFailureRequest request,
                StreamObserver<ReportLoginFailureResponse> responseObserver) {
            responseObserver.onNext(ReportLoginFailureResponse.newBuilder().build());
            responseObserver.onCompleted();
        }
    }
}
