package com.bankops.portal.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.bankops.portal.entity.Account;
import com.bankops.portal.entity.Customer;
import com.bankops.portal.entity.Transaction;
import com.bankops.portal.repository.AccountRepository;
import com.bankops.portal.repository.CustomerRepository;
import com.bankops.portal.repository.SupportCaseRepository;
import com.bankops.portal.repository.TransactionRepository;
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
 * End-to-end integration test for the fluxguard rate-limit gate on the HELD-transaction
 * ops actions (release / reject) inside {@code TransactionService}. Replaces the real
 * fluxguard gRPC channel with an in-process server that always returns DECISION_DENY,
 * then exercises the controller via {@code MockMvc} to assert the ops action fails fast
 * with HTTP 429 and that the transaction is NOT mutated (stays HELD; balance unchanged) —
 * i.e. the rate-limit gate runs before any state change.
 */
@SpringBootTest(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "bankops.fluxguard.enabled=true",
        "bankops.fluxguard.deadline=PT5S"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class OpsRateLimitGateIntegrationTest {

    private static final String INPROC_SERVER_NAME = "ops-ratelimit-gate-integration-test";
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
    private CustomerRepository customerRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private SupportCaseRepository supportCaseRepository;

    private Account account;
    private Transaction heldTransaction;

    @BeforeEach
    void setUp() {
        supportCaseRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        customerRepository.deleteAll();

        Customer customer = customerRepository.save(Customer.builder()
                .firstName("Ops")
                .lastName("RateLimit")
                .email("ops.ratelimit@example.com")
                .phone("555-8888")
                .build());

        account = accountRepository.save(Account.builder()
                .customer(customer)
                .type(Account.AccountType.CHEQUING)
                .status(Account.AccountStatus.OPEN)
                .balance(new BigDecimal("50000.00"))
                .overdraftEnabled(false)
                .build());

        heldTransaction = transactionRepository.save(Transaction.builder()
                .account(account)
                .type(Transaction.TransactionType.DEPOSIT)
                .amount(new BigDecimal("100.00"))
                .status(Transaction.TransactionStatus.HELD)
                .correlationId(UUID.randomUUID().toString())
                .build());
    }

    @Test
    @WithMockUser(roles = "SUPPORT")
    void deniedRateLimit_releaseReturns429AndLeavesTransactionHeld() throws Exception {
        mockMvc.perform(post("/accounts/{accountId}/transactions/{txId}/release",
                        account.getId(), heldTransaction.getId()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3"));

        // Gate runs before any mutation: status stays HELD and the balance is untouched.
        Transaction reloaded = transactionRepository.findById(heldTransaction.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(reloaded.getStatus())
                .isEqualTo(Transaction.TransactionStatus.HELD);
        org.assertj.core.api.Assertions.assertThat(
                        accountRepository.findById(account.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("50000.00");
    }

    @Test
    @WithMockUser(roles = "SUPPORT")
    void deniedRateLimit_rejectReturns429AndLeavesTransactionHeld() throws Exception {
        mockMvc.perform(post("/accounts/{accountId}/transactions/{txId}/reject",
                        account.getId(), heldTransaction.getId()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3"));

        Transaction reloaded = transactionRepository.findById(heldTransaction.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(reloaded.getStatus())
                .isEqualTo(Transaction.TransactionStatus.HELD);
    }

    /** In-process gRPC service that always denies with a 2500ms retry-after. */
    static class DenyingRateLimitService extends RateLimitGrpc.RateLimitImplBase {
        @Override
        public void checkLimit(CheckLimitRequest request,
                StreamObserver<CheckLimitResponse> responseObserver) {
            responseObserver.onNext(CheckLimitResponse.newBuilder()
                    .setDecision(Decision.DECISION_DENY)
                    .setRetryAfterMs(2500L)
                    .setPolicyApplied(request.getPolicy().name())
                    .build());
            responseObserver.onCompleted();
        }
    }
}
