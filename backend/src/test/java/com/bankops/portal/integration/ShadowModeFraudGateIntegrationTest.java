package com.bankops.portal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

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
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;

/**
 * Shadow-mode variant of {@link FraudGateIntegrationTest}. Bootstrapped with
 * {@code bankops.fluxa.shadow-mode=true} so a Fluxa FLAG response is logged
 * but does NOT divert the transaction to HELD — the success path proceeds and
 * the balance mutates as if Fluxa had returned ALLOW.
 *
 * <p>Separate test class (not a {@code @Nested} inner class) because Spring
 * picks up {@code @TestPropertySource} / {@code @SpringBootTest(properties=…)}
 * at the test class level and would ignore overrides on a nested inner.
 */
@SpringBootTest(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "bankops.fluxa.enabled=true",
        "bankops.fluxa.shadow-mode=true",
        "bankops.fluxa.deadline=PT5S"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ShadowModeFraudGateIntegrationTest {

    private static final String INPROC_SERVER_NAME = "shadow-fraud-gate-test";
    private static Server inProcessServer;

    @TestConfiguration
    static class FluxaInProcessConfig {

        @Bean
        public ShadowFraudEvalService shadowFraudEvalService() {
            return new ShadowFraudEvalService();
        }

        @Bean
        public Server inProcessFluxaServer(ShadowFraudEvalService service) throws Exception {
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

    private Account account;

    @BeforeEach
    void setUp() {
        supportCaseRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        customerRepository.deleteAll();

        Customer customer = customerRepository.save(Customer.builder()
                .firstName("Shadow")
                .lastName("Test")
                .email("shadow.test@example.com")
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

    @Test
    @WithMockUser(roles = "USER")
    void shadowMode_FlagResponseStillReturns201AndMutatesBalance() throws Exception {
        // Stub Fluxa always returns FLAG — shadow mode should ignore and complete.
        CreateTransactionRequest req = new CreateTransactionRequest();
        req.setType("DEPOSIT");
        req.setAmount(new BigDecimal("12500.00"));
        req.setDescription("luxury car");

        mockMvc.perform(post("/accounts/{id}/transactions", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getBalance()).isEqualByComparingTo(new BigDecimal("62500.00"));
        assertThat(supportCaseRepository.findAll()).isEmpty();
    }

    /**
     * Always responds with a FLAG so the test exercises the shadow-mode swallow.
     */
    static class ShadowFraudEvalService extends FraudEvalGrpc.FraudEvalImplBase {
        @Override
        public void evaluateTransaction(EvaluateRequest request,
                StreamObserver<EvaluateResponse> responseObserver) {
            EvaluateResponse resp = EvaluateResponse.newBuilder()
                    .setDecision(Decision.DECISION_FLAG)
                    .addFlags(FraudFlag.newBuilder()
                            .setRuleName("amount_threshold")
                            .setRuleValue("amount=12500 > 500"))
                    .setEvaluatedBy("shadow-stub-flag")
                    .setLatencyMs(3.0)
                    .build();
            responseObserver.onNext(resp);
            responseObserver.onCompleted();
        }
    }
}
