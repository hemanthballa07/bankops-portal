package com.bankops.portal.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;

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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.bankops.portal.client.fluxa.FluxaFraudClient;
import com.bankops.portal.dto.CreateTransactionRequest;
import com.bankops.portal.entity.Account;
import com.bankops.portal.entity.Customer;
import com.bankops.portal.repository.AccountRepository;
import com.bankops.portal.repository.CustomerRepository;
import com.bankops.portal.repository.SupportCaseRepository;
import com.bankops.portal.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * End-to-end integration test for the fluxguard rate-limit gate inside
 * {@code TransactionService}. Replaces the real fluxguard gRPC channel with an
 * in-process server that always returns DECISION_DENY, then exercises the controller
 * via {@code MockMvc} to assert the request fails fast with HTTP 429 and that the
 * Fluxa fraud RPC is never reached (the rate-limit gate runs first).
 *
 * <p>Fluxa is left enabled so the deposit path reaches the rate-limit gate that sits
 * immediately before the fraud RPC; the {@code FluxaFraudClient} spy proves the gate
 * short-circuits before fraud evaluation.
 */
@SpringBootTest(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "bankops.fluxguard.enabled=true",
        "bankops.fluxguard.deadline=PT5S",
        // Fluxa enabled so the deposit fraud path (which the rate-limit gate guards)
        // is active; the spy asserts it is never invoked once rate-limit denies.
        "bankops.fluxa.enabled=true",
        "bankops.fluxa.deadline=PT5S"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class RateLimitGateIntegrationTest {

    private static final String INPROC_SERVER_NAME = "ratelimit-gate-integration-test";
    private static Server inProcessServer;

    @TestConfiguration
    static class FluxguardInProcessConfig {

        @Bean
        public DenyingRateLimitService denyingRateLimitService() {
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
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private SupportCaseRepository supportCaseRepository;

    @SpyBean
    private FluxaFraudClient fluxaFraudClient;

    private Account account;

    @BeforeEach
    void setUp() {
        supportCaseRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        customerRepository.deleteAll();

        Customer customer = customerRepository.save(Customer.builder()
                .firstName("Rate")
                .lastName("Limit")
                .email("rate.limit@example.com")
                .phone("555-7777")
                .build());

        account = accountRepository.save(Account.builder()
                .customer(customer)
                .type(Account.AccountType.CHEQUING)
                .status(Account.AccountStatus.OPEN)
                .balance(new BigDecimal("50000.00"))
                .overdraftEnabled(false)
                .build());
    }

    @Test
    @WithMockUser(roles = "USER")
    void deniedRateLimit_Returns429AndNeverCallsFluxa() throws Exception {
        CreateTransactionRequest req = new CreateTransactionRequest();
        req.setType("DEPOSIT");
        req.setAmount(new BigDecimal("100.00"));
        req.setDescription("integration test");

        mockMvc.perform(post("/accounts/{id}/transactions", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3"));

        // The rate-limit gate runs BEFORE the fraud RPC, so Fluxa is never evaluated.
        verify(fluxaFraudClient, never()).evaluate(
                anyString(), anyLong(), any(BigDecimal.class), anyString(), anyString(),
                any(Instant.class));

        // No transaction persisted.
        org.assertj.core.api.Assertions.assertThat(transactionRepository.findAll()).isEmpty();
    }

    /** In-process gRPC service that always denies with a 2500ms retry-after. */
    static class DenyingRateLimitService extends RateLimitGrpc.RateLimitImplBase {
        @Override
        public void checkLimit(CheckLimitRequest request,
                StreamObserver<CheckLimitResponse> responseObserver) {
            responseObserver.onNext(CheckLimitResponse.newBuilder()
                    .setDecision(Decision.DECISION_DENY)
                    .setRetryAfterMs(2500L)
                    .setPolicyApplied("transaction")
                    .build());
            responseObserver.onCompleted();
        }
    }
}
