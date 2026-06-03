package com.bankops.portal.integration;

import com.bankops.portal.entity.Account;
import com.bankops.portal.entity.Customer;
import com.bankops.portal.entity.SupportCase;
import com.bankops.portal.entity.Transaction;
import com.bankops.portal.repository.AccountRepository;
import com.bankops.portal.repository.CustomerRepository;
import com.bankops.portal.repository.MlRiskBandConfigRepository;
import com.bankops.portal.repository.SupportCaseRepository;
import com.bankops.portal.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ReportsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private SupportCaseRepository supportCaseRepository;
    @Autowired private MlRiskBandConfigRepository mlRiskBandConfigRepository;

    private Account account;

    @BeforeEach
    void setUp() {
        mlRiskBandConfigRepository.deleteAll(); // deterministic 0.40/0.70 bands (shared test context)
        supportCaseRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        customerRepository.deleteAll();

        Customer customer = customerRepository.save(Customer.builder()
                .firstName("Rep").lastName("Test").email("rep.test@example.com").phone("555-0000").build());
        account = accountRepository.save(Account.builder()
                .customer(customer).type(Account.AccountType.CHEQUING)
                .status(Account.AccountStatus.OPEN).balance(new BigDecimal("1000.00"))
                .overdraftEnabled(false).build());

        // 2 COMPLETED, 1 HELD transaction
        saveTx(Transaction.TransactionStatus.COMPLETED);
        saveTx(Transaction.TransactionStatus.COMPLETED);
        saveTx(Transaction.TransactionStatus.HELD);

        // 1 HIGH-severity case
        supportCaseRepository.save(SupportCase.builder()
                .customer(customer)
                .severity(SupportCase.CaseSeverity.HIGH)
                .summary("seed fraud case")
                .build());
    }

    private void saveTx(Transaction.TransactionStatus s) {
        transactionRepository.save(Transaction.builder()
                .account(account)
                .type(Transaction.TransactionType.DEPOSIT)
                .amount(new BigDecimal("10.00"))
                .status(s)
                .correlationId(java.util.UUID.randomUUID().toString())
                .build());
    }

    private void saveScoredTx(double mlScore) {
        transactionRepository.save(Transaction.builder()
                .account(account)
                .type(Transaction.TransactionType.DEPOSIT)
                .amount(new BigDecimal("10.00"))
                .status(Transaction.TransactionStatus.HELD)
                .correlationId(java.util.UUID.randomUUID().toString())
                .mlScore(mlScore)
                .build());
    }

    @Test
    @WithMockUser(roles = "SUPPORT")
    void summary_returnsTransactionAndCaseAggregates() throws Exception {
        mockMvc.perform(get("/reports/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionsByStatus.COMPLETED").value(2))
                .andExpect(jsonPath("$.transactionsByStatus.HELD").value(1))
                .andExpect(jsonPath("$.totalTransactions").value(3))
                .andExpect(jsonPath("$.casesBySeverity.HIGH").value(1))
                .andExpect(jsonPath("$.totalCases").value(1))
                .andExpect(jsonPath("$.caseKpis").exists());
    }

    @Test
    @WithMockUser(roles = "SUPPORT")
    void summary_bucketsMlScoresByConfiguredBands() throws Exception {
        saveScoredTx(0.2);  // LOW  (< 0.40)
        saveScoredTx(0.3);  // LOW
        saveScoredTx(0.5);  // MED  (0.40–0.70)
        saveScoredTx(0.85); // HIGH (>= 0.70)
        saveScoredTx(0.9);  // HIGH

        mockMvc.perform(get("/reports/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mlRiskByBand.LOW").value(2))
                .andExpect(jsonPath("$.mlRiskByBand.MED").value(1))
                .andExpect(jsonPath("$.mlRiskByBand.HIGH").value(2));
    }
}
