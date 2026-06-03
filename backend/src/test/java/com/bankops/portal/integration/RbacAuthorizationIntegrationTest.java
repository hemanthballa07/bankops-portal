package com.bankops.portal.integration;

import com.bankops.portal.entity.Account;
import com.bankops.portal.entity.Customer;
import com.bankops.portal.entity.Transaction;
import com.bankops.portal.repository.AccountRepository;
import com.bankops.portal.repository.CustomerRepository;
import com.bankops.portal.repository.SupportCaseRepository;
import com.bankops.portal.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization-only coverage (status codes, not business logic) for the RBAC hardening:
 * a read-only USER must not release/reject HELD transactions or read raw log events.
 * Runs under the default {@code test} profile (no {@code local} console bypass).
 * fluxguard is disabled in {@code test} (fail-open), so the SUPPORT release flows like
 * the end-to-end positive control in ReviewNotesAuditIntegrationTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RbacAuthorizationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AccountRepository accountRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired SupportCaseRepository supportCaseRepository;

    private Long accountId;
    private Long heldTxnId;

    @BeforeEach
    void setUp() {
        supportCaseRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        customerRepository.deleteAll();
        Customer customer = customerRepository.save(Customer.builder()
                .firstName("Rbac").lastName("Tester").email("rbac.tester@example.com").phone("555-0000").build());
        Account a = accountRepository.save(Account.builder()
                .customer(customer).type(Account.AccountType.CHEQUING)
                .status(Account.AccountStatus.OPEN).balance(new BigDecimal("1000.00")).overdraftEnabled(false).build());
        accountId = a.getId();
        Transaction t = transactionRepository.save(Transaction.builder()
                .account(a).type(Transaction.TransactionType.DEPOSIT).amount(new BigDecimal("50.00"))
                .status(Transaction.TransactionStatus.HELD).correlationId("rbac-corr").build());
        heldTxnId = t.getId();
    }

    @Test
    @WithMockUser(roles = "USER")
    void userCannotReleaseHeldTransaction() throws Exception {
        mockMvc.perform(post("/accounts/{a}/transactions/{t}/release", accountId, heldTxnId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void userCannotRejectHeldTransaction() throws Exception {
        mockMvc.perform(post("/accounts/{a}/transactions/{t}/reject", accountId, heldTxnId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPPORT")
    void supportCanReleaseHeldTransaction() throws Exception {
        mockMvc.perform(post("/accounts/{a}/transactions/{t}/release", accountId, heldTxnId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void userCannotReadLogEvents() throws Exception {
        mockMvc.perform(get("/log-events/by-correlation/{c}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isForbidden());
    }
}
