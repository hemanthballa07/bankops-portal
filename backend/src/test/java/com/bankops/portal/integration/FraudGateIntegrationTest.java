package com.bankops.portal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.bankops.portal.dto.CreateTransactionRequest;
import com.bankops.portal.entity.Account;
import com.bankops.portal.entity.Customer;
import com.bankops.portal.entity.SupportCase;
import com.bankops.portal.repository.AccountRepository;
import com.bankops.portal.repository.CustomerRepository;
import com.bankops.portal.repository.SupportCaseRepository;
import com.bankops.portal.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxa.fraud.v1.Decision;
import com.fluxa.fraud.v1.EvaluateRequest;
import com.fluxa.fraud.v1.EvaluateResponse;
import com.fluxa.fraud.v1.FraudEvalGrpc;
import com.fluxa.fraud.v1.FraudFlag;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;

/**
 * End-to-end integration test for the Fluxa fraud-eval gate inside
 * {@code TransactionService}. Replaces the real gRPC channel with an in-process
 * server whose response can be set per-test, then exercises the controller via
 * {@code MockMvc} to assert HTTP-level behavior.
 *
 * <p>Test profile config ({@code application-test.yaml}): {@code enabled=true,
 * shadowMode=false, withdrawal=FAIL_CLOSED, deposit=FAIL_CLOSED}.
 */
@SpringBootTest(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        // Test profile defaults to disabled; this class exercises the gate so
        // explicitly re-enable.
        "bankops.fluxa.enabled=true",
        // 80ms is realistic against a live fluxa server but JVM cold-start +
        // first-RPC overhead can exceed that in an in-process test channel.
        "bankops.fluxa.deadline=PT5S"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class FraudGateIntegrationTest {

    private static final String INPROC_SERVER_NAME = "fraud-gate-integration-test";
    private static Server inProcessServer;

    @TestConfiguration
    static class FluxaInProcessConfig {

        @Bean
        public MutableFraudEvalService mutableFraudEvalService() {
            return new MutableFraudEvalService();
        }

        @Bean
        public Server inProcessFluxaServer(MutableFraudEvalService service) throws Exception {
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
        public ManagedChannel fluxaManagedChannel(Server inProcessFluxaServer) {
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
    @Autowired
    private MutableFraudEvalService fluxaStub;

    private Account account;

    @BeforeEach
    void setUp() {
        supportCaseRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        customerRepository.deleteAll();

        Customer customer = customerRepository.save(Customer.builder()
                .firstName("Gate")
                .lastName("Test")
                .email("gate.test@example.com")
                .phone("555-1234")
                .build());

        account = accountRepository.save(Account.builder()
                .customer(customer)
                .type(Account.AccountType.CHEQUING)
                .status(Account.AccountStatus.OPEN)
                .balance(new BigDecimal("50000.00"))
                .overdraftEnabled(false)
                .build());

        fluxaStub.reset();
    }

    @Test
    @WithMockUser(roles = "USER")
    void allow_DepositReturns201CompletedAndMutatesBalance() throws Exception {
        fluxaStub.respondAllow();
        CreateTransactionRequest req = depositRequest(new BigDecimal("50.00"));

        mockMvc.perform(post("/accounts/{id}/transactions", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getBalance()).isEqualByComparingTo(new BigDecimal("50050.00"));
        assertThat(supportCaseRepository.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(roles = "USER")
    void flag_DepositReturns202HeldAndCreatesCase() throws Exception {
        fluxaStub.respondFlag("amount_threshold", "amount=12500 > 500");
        CreateTransactionRequest req = depositRequest(new BigDecimal("12500.00"));

        mockMvc.perform(post("/accounts/{id}/transactions", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("HELD"));

        // Balance untouched
        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getBalance()).isEqualByComparingTo(new BigDecimal("50000.00"));

        // Exactly one fraud case
        List<SupportCase> cases = supportCaseRepository.findAll();
        assertThat(cases).hasSize(1);
        assertThat(cases.get(0).getSeverity()).isEqualTo(SupportCase.CaseSeverity.HIGH);
    }

    @Test
    @WithMockUser(roles = "USER")
    void unavailable_DepositReturns503UnderFailClosed() throws Exception {
        // Test profile = FAIL_CLOSED for both directions.
        fluxaStub.respondError(Status.UNAVAILABLE);
        CreateTransactionRequest req = depositRequest(new BigDecimal("100.00"));

        mockMvc.perform(post("/accounts/{id}/transactions", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isServiceUnavailable());

        // No partial side effects: balance unchanged, no Transaction row.
        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getBalance()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(transactionRepository.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(roles = "USER")
    void invalidArgument_DepositReturns400() throws Exception {
        fluxaStub.respondError(Status.INVALID_ARGUMENT.withDescription("event_id is required"));
        CreateTransactionRequest req = depositRequest(new BigDecimal("100.00"));

        mockMvc.perform(post("/accounts/{id}/transactions", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void withdrawal_IdempotencyReplayDoesNotReevaluateFluxa() throws Exception {
        fluxaStub.respondAllow();
        CreateTransactionRequest req = new CreateTransactionRequest();
        req.setType("WITHDRAWAL");
        req.setAmount(new BigDecimal("100.00"));
        req.setDescription("atm");

        // First call — should reach Fluxa
        mockMvc.perform(post("/accounts/{id}/transactions", account.getId())
                        .header("Idempotency-Key", "key-rep")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
        assertThat(fluxaStub.evaluateCount()).isEqualTo(1);

        // Replay with the same Idempotency-Key — should NOT reach Fluxa again
        mockMvc.perform(post("/accounts/{id}/transactions", account.getId())
                        .header("Idempotency-Key", "key-rep")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
        assertThat(fluxaStub.evaluateCount()).isEqualTo(1);
    }

    private CreateTransactionRequest depositRequest(BigDecimal amount) {
        CreateTransactionRequest req = new CreateTransactionRequest();
        req.setType("DEPOSIT");
        req.setAmount(amount);
        req.setDescription("integration test");
        return req;
    }

    /**
     * In-process gRPC service whose response is settable per-test. Counts the
     * number of evaluate calls so tests can assert dedup invariants.
     */
    static class MutableFraudEvalService extends FraudEvalGrpc.FraudEvalImplBase {

        private EvaluateResponse response;
        private Throwable error;
        private int callCount;

        void reset() {
            this.response = null;
            this.error = null;
            this.callCount = 0;
        }

        void respondAllow() {
            this.error = null;
            this.response = EvaluateResponse.newBuilder()
                    .setDecision(Decision.DECISION_ALLOW)
                    .setEvaluatedBy("test-stub-allow")
                    .setLatencyMs(5.0)
                    .build();
        }

        void respondFlag(String ruleName, String ruleValue) {
            this.error = null;
            this.response = EvaluateResponse.newBuilder()
                    .setDecision(Decision.DECISION_FLAG)
                    .addFlags(FraudFlag.newBuilder()
                            .setRuleName(ruleName)
                            .setRuleValue(ruleValue))
                    .setEvaluatedBy("test-stub-flag")
                    .setLatencyMs(5.0)
                    .build();
        }

        void respondError(Status status) {
            this.response = null;
            this.error = status.asRuntimeException();
        }

        int evaluateCount() {
            return callCount;
        }

        @Override
        public void evaluateTransaction(EvaluateRequest request,
                StreamObserver<EvaluateResponse> responseObserver) {
            callCount++;
            if (error != null) {
                responseObserver.onError(error);
                return;
            }
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}
