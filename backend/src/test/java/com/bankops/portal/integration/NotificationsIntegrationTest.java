package com.bankops.portal.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.bankops.portal.entity.Account;
import com.bankops.portal.entity.Customer;
import com.bankops.portal.entity.SupportCase;
import com.bankops.portal.entity.Transaction;
import com.bankops.portal.repository.AccountRepository;
import com.bankops.portal.repository.CustomerRepository;
import com.bankops.portal.repository.SupportCaseRepository;
import com.bankops.portal.repository.TransactionRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class NotificationsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private SupportCaseRepository supportCaseRepository;

    @BeforeEach
    void setUp() {
        supportCaseRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        customerRepository.deleteAll();

        Customer customer = customerRepository.save(Customer.builder()
                .firstName("Notify").lastName("Test").email("notify.test@example.com")
                .phone("555-0001").build());
        Account account = accountRepository.save(Account.builder()
                .customer(customer).type(Account.AccountType.CHEQUING)
                .status(Account.AccountStatus.OPEN).balance(new BigDecimal("1000.00"))
                .overdraftEnabled(false).build());

        transactionRepository.save(Transaction.builder()
                .account(account).type(Transaction.TransactionType.DEPOSIT)
                .amount(new BigDecimal("99999.00")).status(Transaction.TransactionStatus.HELD)
                .correlationId(java.util.UUID.randomUUID().toString()).build());

        supportCaseRepository.save(SupportCase.builder()
                .customer(customer).severity(SupportCase.CaseSeverity.HIGH)
                .summary("unassigned high case").build());
    }

    @Test
    @WithMockUser(roles = "SUPPORT")
    void notifications_returnsItemsAndCounts() throws Exception {
        mockMvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.counts.critical").value(1))   // the HELD fraud hold
                .andExpect(jsonPath("$.counts.warning").value(1))    // the unassigned HIGH case
                .andExpect(jsonPath("$.counts.info").value(1));      // backlog rollup
    }

    @Test
    void notifications_requiresAuth() throws Exception {
        mockMvc.perform(get("/notifications"))
                .andExpect(status().is4xxClientError()); // 401/403 when unauthenticated
    }
}
