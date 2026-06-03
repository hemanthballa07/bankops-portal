package com.bankops.portal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.bankops.portal.entity.Account;
import com.bankops.portal.entity.AuditEvent;
import com.bankops.portal.entity.Customer;
import com.bankops.portal.entity.Transaction;
import com.bankops.portal.repository.AccountRepository;
import com.bankops.portal.repository.AuditEventRepository;
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
 * End-to-end coverage for the release/reject "review" path the trifecta-console drives.
 * A POST carrying a {@code ReviewTransactionRequest} body ({@code {actorId, notes}}) must
 * (1) succeed, (2) move the HELD transaction to RELEASED/REJECTED, and (3) record a
 * TRANSACTION {@link AuditEvent} whose newValue carries the actor and notes — so ops
 * decisions are attributable in the audit timeline. The empty-body case pins the
 * backward-compatible default (actor=SYSTEM, no notes). The fluxguard ops rate-limit gate
 * is replaced with an in-process ALLOW server so the action proceeds past the gate.
 */
@SpringBootTest(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "bankops.fluxguard.enabled=true",
        "bankops.fluxguard.deadline=PT5S"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ReviewNotesAuditIntegrationTest {

    private static final String INPROC_SERVER_NAME = "ops-review-notes-integration-test";
    private static Server inProcessServer;

    @TestConfiguration
    static class FluxguardInProcessConfig {

        @Bean
        public AllowingRateLimitService allowingRateLimitService() {
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
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private SupportCaseRepository supportCaseRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private Account account;

    @BeforeEach
    void setUp() {
        supportCaseRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        customerRepository.deleteAll();

        Customer customer = customerRepository.save(Customer.builder()
                .firstName("Ops")
                .lastName("Review")
                .email("ops.review@example.com")
                .phone("555-9999")
                .build());

        account = accountRepository.save(Account.builder()
                .customer(customer)
                .type(Account.AccountType.CHEQUING)
                .status(Account.AccountStatus.OPEN)
                .balance(new BigDecimal("50000.00"))
                .overdraftEnabled(false)
                .build());
    }

    private Transaction newHeldTransaction() {
        return transactionRepository.save(Transaction.builder()
                .account(account)
                .type(Transaction.TransactionType.DEPOSIT)
                .amount(new BigDecimal("100.00"))
                .status(Transaction.TransactionStatus.HELD)
                .correlationId(UUID.randomUUID().toString())
                .build());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> latestTransactionAudit(Long txnId) throws Exception {
        AuditEvent event = auditEventRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(
                        AuditEvent.EntityType.TRANSACTION, txnId, PageRequest.of(0, 1))
                .getContent().get(0);
        assertThat(event.getAction()).isEqualTo(AuditEvent.Action.UPDATE);
        return objectMapper.readValue(event.getNewValue(), Map.class);
    }

    @Test
    @WithMockUser(roles = "SUPPORT")
    void release_withReviewBody_persistsActorAndNotesToAudit() throws Exception {
        Transaction held = newHeldTransaction();

        mockMvc.perform(post("/accounts/{accountId}/transactions/{txId}/release",
                        account.getId(), held.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actorId\":\"trifecta-console\",\"notes\":\"verified by caller\"}"))
                .andExpect(status().isOk());

        assertThat(transactionRepository.findById(held.getId()).orElseThrow().getStatus())
                .isEqualTo(Transaction.TransactionStatus.RELEASED);

        Map<String, Object> audit = latestTransactionAudit(held.getId());
        assertThat(audit)
                .containsEntry("status", "RELEASED")
                .containsEntry("actor", "trifecta-console")
                .containsEntry("notes", "verified by caller");
    }

    @Test
    @WithMockUser(roles = "SUPPORT")
    void reject_withReviewBody_persistsActorAndNotesToAudit() throws Exception {
        Transaction held = newHeldTransaction();

        mockMvc.perform(post("/accounts/{accountId}/transactions/{txId}/reject",
                        account.getId(), held.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actorId\":\"trifecta-console\",\"notes\":\"fraud_confirmed - chargeback filed\"}"))
                .andExpect(status().isOk());

        assertThat(transactionRepository.findById(held.getId()).orElseThrow().getStatus())
                .isEqualTo(Transaction.TransactionStatus.REJECTED);

        Map<String, Object> audit = latestTransactionAudit(held.getId());
        assertThat(audit)
                .containsEntry("status", "REJECTED")
                .containsEntry("actor", "trifecta-console")
                .containsEntry("notes", "fraud_confirmed - chargeback filed");
    }

    @Test
    @WithMockUser(roles = "SUPPORT")
    void release_withEmptyBody_defaultsActorToSystemAndOmitsNotes() throws Exception {
        Transaction held = newHeldTransaction();

        mockMvc.perform(post("/accounts/{accountId}/transactions/{txId}/release",
                        account.getId(), held.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        Map<String, Object> audit = latestTransactionAudit(held.getId());
        assertThat(audit).containsEntry("actor", "SYSTEM");
        assertThat(audit).doesNotContainKey("notes");
    }

    /** In-process gRPC service that always allows, so the ops action proceeds past the gate. */
    static class AllowingRateLimitService extends RateLimitGrpc.RateLimitImplBase {
        @Override
        public void checkLimit(CheckLimitRequest request,
                StreamObserver<CheckLimitResponse> responseObserver) {
            responseObserver.onNext(CheckLimitResponse.newBuilder()
                    .setDecision(Decision.DECISION_ALLOW)
                    .setPolicyApplied(request.getPolicy().name())
                    .build());
            responseObserver.onCompleted();
        }
    }
}
