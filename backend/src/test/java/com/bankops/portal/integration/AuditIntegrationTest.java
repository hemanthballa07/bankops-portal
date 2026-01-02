package com.bankops.portal.integration;

import com.bankops.portal.dto.CreateTransactionRequest;
import com.bankops.portal.dto.UpdateAccountRequest;
import com.bankops.portal.entity.Account;
import com.bankops.portal.entity.AuditEvent;
import com.bankops.portal.entity.Customer;
import com.bankops.portal.repository.AccountRepository;
import com.bankops.portal.repository.AuditEventRepository;
import com.bankops.portal.repository.CustomerRepository;
import com.bankops.portal.service.AccountService;
import com.bankops.portal.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private Customer testCustomer;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        // Create test customer
        testCustomer = Customer.builder()
                .firstName("Test")
                .lastName("User")
                .email("test@example.com")
                .phone("1234567890")
                .build();
        testCustomer = customerRepository.save(testCustomer);

        // Create test account
        testAccount = Account.builder()
                .customer(testCustomer)
                .type(Account.AccountType.CHEQUING)
                .status(Account.AccountStatus.OPEN)
                .balance(BigDecimal.valueOf(1000.00))
                .overdraftEnabled(false)
                .build();
        testAccount = accountRepository.save(testAccount);
    }

    @Test
    void testTransactionCreatesAuditEvent() {
        // Arrange
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("DEPOSIT");
        request.setAmount(BigDecimal.valueOf(100.00));
        request.setDescription("Test deposit");
        request.setCategory("OTHER");

        // Act
        transactionService.createTransaction(testAccount.getId(), request, null);

        // Assert
        Page<AuditEvent> auditEvents = auditEventRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(
                AuditEvent.EntityType.ACCOUNT,
                testAccount.getId(),
                PageRequest.of(0, 10));

        assertFalse(auditEvents.isEmpty());
        AuditEvent event = auditEvents.getContent().get(0);
        assertEquals(AuditEvent.EntityType.ACCOUNT, event.getEntityType());
        assertEquals(testAccount.getId(), event.getEntityId());
        assertEquals(AuditEvent.Action.UPDATE, event.getAction());
        assertNotNull(event.getNewValue());
        assertTrue(event.getNewValue().contains("1100")); // New balance
    }

    @Test
    void testAccountStatusChangeCreatesAuditEvent() {
        // Arrange
        UpdateAccountRequest request = new UpdateAccountRequest();
        request.setStatus("CLOSED");

        // Act
        accountService.updateAccount(testAccount.getId(), request);

        // Assert
        Page<AuditEvent> auditEvents = auditEventRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(
                AuditEvent.EntityType.ACCOUNT,
                testAccount.getId(),
                PageRequest.of(0, 10));

        assertFalse(auditEvents.isEmpty());
        AuditEvent event = auditEvents.getContent().get(0);
        assertEquals(AuditEvent.EntityType.ACCOUNT, event.getEntityType());
        assertEquals(testAccount.getId(), event.getEntityId());
        assertEquals(AuditEvent.Action.STATUS_CHANGE, event.getAction());
        assertTrue(event.getOldValue().contains("OPEN"));
        assertTrue(event.getNewValue().contains("CLOSED"));
    }

    @Test
    void testMultipleTransactionsCreateMultipleAuditEvents() {
        // Arrange & Act
        for (int i = 0; i < 5; i++) {
            CreateTransactionRequest request = new CreateTransactionRequest();
            request.setType("DEPOSIT");
            request.setAmount(BigDecimal.valueOf(10.00));
            request.setDescription("Deposit " + i);
            transactionService.createTransaction(testAccount.getId(), request, null);
        }

        // Assert
        Page<AuditEvent> auditEvents = auditEventRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(
                AuditEvent.EntityType.ACCOUNT,
                testAccount.getId(),
                PageRequest.of(0, 10));

        assertEquals(5, auditEvents.getTotalElements());
    }

    @Test
    void testAuditEventsPagination() {
        // Arrange & Act - Create 25 transactions
        for (int i = 0; i < 25; i++) {
            CreateTransactionRequest request = new CreateTransactionRequest();
            request.setType("DEPOSIT");
            request.setAmount(BigDecimal.valueOf(1.00));
            transactionService.createTransaction(testAccount.getId(), request, null);
        }

        // Assert - First page
        Page<AuditEvent> page1 = auditEventRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(
                AuditEvent.EntityType.ACCOUNT,
                testAccount.getId(),
                PageRequest.of(0, 20));

        assertEquals(20, page1.getContent().size());
        assertEquals(25, page1.getTotalElements());
        assertEquals(2, page1.getTotalPages());

        // Assert - Second page
        Page<AuditEvent> page2 = auditEventRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(
                AuditEvent.EntityType.ACCOUNT,
                testAccount.getId(),
                PageRequest.of(1, 20));

        assertEquals(5, page2.getContent().size());
    }
}
