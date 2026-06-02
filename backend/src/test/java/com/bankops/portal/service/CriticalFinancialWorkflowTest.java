package com.bankops.portal.service;

import com.bankops.portal.client.fluxa.FluxaEvalOutcome;
import com.bankops.portal.client.fluxa.FluxaFraudClient;
import com.bankops.portal.config.FluxaProperties;
import com.bankops.portal.dto.CreateTransactionRequest;
import com.bankops.portal.dto.TransactionDto;
import com.bankops.portal.entity.Account;
import com.bankops.portal.entity.Customer;
import com.bankops.portal.entity.LogEvent;
import com.bankops.portal.entity.Transaction;
import com.bankops.portal.repository.AccountRepository;
import com.bankops.portal.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Critical Financial Workflow Tests")
class CriticalFinancialWorkflowTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LoggingService loggingService;

    @Mock
    private AuditEventService auditEventService;

    @Mock
    private FluxaFraudClient fluxaFraudClient;

    @Mock
    private com.bankops.portal.client.fluxguard.FluxguardRateLimitClient fluxguardRateLimitClient;

    @Mock
    private CaseService caseService;

    private final FluxaProperties fluxaProperties = new FluxaProperties(
            false, false, "localhost", 9090,
            Duration.ofMillis(80),
            FluxaProperties.Failure.FAIL_OPEN,
            FluxaProperties.Failure.FAIL_OPEN);

    private TransactionService transactionService;

    private Account testAccount;
    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        testCustomer = Customer.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .phone("555-1234")
                .build();

        testAccount = Account.builder()
                .id(100L)
                .customer(testCustomer)
                .type(Account.AccountType.CHEQUING)
                .status(Account.AccountStatus.OPEN)
                .balance(new BigDecimal("1000.00"))
                .overdraftEnabled(false)
                .createdAt(LocalDateTime.now())
                .build();

        transactionService = new TransactionService(
                transactionRepository, accountRepository, loggingService,
                auditEventService, fluxaFraudClient, fluxaProperties,
                fluxguardRateLimitClient, caseService);
    }

    // ========== CRITICAL: Transaction Status Transitions ==========

    @Test
    @DisplayName("🔴 CRITICAL: Transaction must transition from PENDING to COMPLETED")
    void testTransactionStatusTransition_PendingToCompleted() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("DEPOSIT");
        request.setAmount(new BigDecimal("100.00"));
        request.setCategory("OTHER");

        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            if (t.getId() == null) t.setId(1L);
            return t;
        });
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        TransactionDto result = transactionService.createTransaction(100L, request, null);

        assertEquals("COMPLETED", result.getStatus());
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    @Test
    @DisplayName("🔴 CRITICAL: Failed transaction must not update balance if status not COMPLETED")
    void testFailedTransaction_DoesNotUpdateBalance() {
        String idemKey = "idem-failed-tx";
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("WITHDRAWAL");
        request.setAmount(new BigDecimal("2000.00"));
        request.setCategory("OTHER");

        when(transactionRepository.findByAccount_IdAndIdempotencyKeyAndType(
                anyLong(), anyString(), any())).thenReturn(Optional.empty());
        when(fluxaFraudClient.evaluate(any(), any(), any(), any(), any(), any()))
                .thenReturn(new FluxaEvalOutcome.Disabled());
        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));

        assertThrows(IllegalStateException.class,
                () -> transactionService.createTransaction(100L, request, idemKey));

        verify(accountRepository, never()).save(any(Account.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    // ========== CRITICAL: Balance Precision Edge Cases ==========

    @Test
    @DisplayName("🔴 CRITICAL: High precision decimals must not lose cents")
    void testBalancePrecision_HighDecimalPlaces() {
        testAccount.setBalance(new BigDecimal("100.999999"));
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("DEPOSIT");
        request.setAmount(new BigDecimal("0.000001"));
        request.setCategory("OTHER");

        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(1L);
            return t;
        });

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        when(accountRepository.save(accountCaptor.capture())).thenReturn(testAccount);

        transactionService.createTransaction(100L, request, null);

        BigDecimal expectedBalance = new BigDecimal("101.000000");
        assertEquals(0, expectedBalance.compareTo(accountCaptor.getValue().getBalance()),
                "Balance precision must be maintained to 6 decimal places");
    }

    @Test
    @DisplayName("🔴 CRITICAL: Very large amounts must not overflow")
    void testBalancePrecision_VeryLargeAmounts() {
        BigDecimal largeAmount = new BigDecimal("999999999999.99");
        testAccount.setBalance(BigDecimal.ZERO);

        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("DEPOSIT");
        request.setAmount(largeAmount);
        request.setCategory("OTHER");

        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(1L);
            return t;
        });

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        when(accountRepository.save(accountCaptor.capture())).thenReturn(testAccount);

        TransactionDto result = transactionService.createTransaction(100L, request, null);

        assertNotNull(result);
        assertEquals(0, largeAmount.compareTo(accountCaptor.getValue().getBalance()),
                "Large amounts must be handled without overflow");
    }

    @Test
    @DisplayName("🔴 CRITICAL: Withdrawal leaving exactly zero balance must succeed")
    void testBalancePrecision_ExactZeroBalance() {
        String idemKey = "idem-exact-zero";
        testAccount.setBalance(new BigDecimal("50.00"));
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("WITHDRAWAL");
        request.setAmount(new BigDecimal("50.00"));
        request.setCategory("OTHER");

        when(transactionRepository.findByAccount_IdAndIdempotencyKeyAndType(
                anyLong(), anyString(), any())).thenReturn(Optional.empty());
        when(fluxaFraudClient.evaluate(any(), any(), any(), any(), any(), any()))
                .thenReturn(new FluxaEvalOutcome.Disabled());
        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(1L);
            return t;
        });

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        when(accountRepository.save(accountCaptor.capture())).thenReturn(testAccount);

        TransactionDto result = transactionService.createTransaction(100L, request, idemKey);

        assertNotNull(result);
        assertEquals(0, BigDecimal.ZERO.compareTo(accountCaptor.getValue().getBalance()),
                "Exact zero balance must be allowed");
    }

    // ========== CRITICAL: Null Overdraft Handling ==========

    @Test
    @DisplayName("🔴 CRITICAL: Null overdraft must be treated as disabled")
    void testOverdraft_NullTreatedAsDisabled() {
        String idemKey = "idem-null-overdraft";
        testAccount.setOverdraftEnabled(null);
        testAccount.setBalance(new BigDecimal("50.00"));

        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("WITHDRAWAL");
        request.setAmount(new BigDecimal("100.00"));
        request.setCategory("OTHER");

        when(transactionRepository.findByAccount_IdAndIdempotencyKeyAndType(
                anyLong(), anyString(), any())).thenReturn(Optional.empty());
        when(fluxaFraudClient.evaluate(any(), any(), any(), any(), any(), any()))
                .thenReturn(new FluxaEvalOutcome.Disabled());
        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> transactionService.createTransaction(100L, request, idemKey));
        assertTrue(exception.getMessage().contains("Insufficient funds"),
                "Null overdraft must prevent negative balance");
    }

    @Test
    @DisplayName("🔴 CRITICAL: False overdraft must prevent negative balance")
    void testOverdraft_FalsePreventNegativeBalance() {
        String idemKey = "idem-no-overdraft";
        testAccount.setOverdraftEnabled(false);
        testAccount.setBalance(new BigDecimal("50.00"));

        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("WITHDRAWAL");
        request.setAmount(new BigDecimal("50.01"));
        request.setCategory("OTHER");

        when(transactionRepository.findByAccount_IdAndIdempotencyKeyAndType(
                anyLong(), anyString(), any())).thenReturn(Optional.empty());
        when(fluxaFraudClient.evaluate(any(), any(), any(), any(), any(), any()))
                .thenReturn(new FluxaEvalOutcome.Disabled());
        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));

        assertThrows(IllegalStateException.class,
                () -> transactionService.createTransaction(100L, request, idemKey),
                "Even $0.01 overdraft must be prevented when disabled");
    }

    // ========== CRITICAL: Correlation ID Handling ==========

    @Test
    @DisplayName("🔴 CRITICAL: Each transaction must have unique correlation ID")
    void testCorrelationId_UniquePerTransaction() {
        CreateTransactionRequest request1 = new CreateTransactionRequest();
        request1.setType("DEPOSIT");
        request1.setAmount(new BigDecimal("10.00"));
        request1.setCategory("OTHER");

        CreateTransactionRequest request2 = new CreateTransactionRequest();
        request2.setType("DEPOSIT");
        request2.setAmount(new BigDecimal("20.00"));
        request2.setCategory("OTHER");

        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            if (t.getId() == null) t.setId(System.currentTimeMillis());
            return t;
        });
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        TransactionDto result1 = transactionService.createTransaction(100L, request1, null);
        TransactionDto result2 = transactionService.createTransaction(100L, request2, null);

        assertNotEquals(result1.getCorrelationId(), result2.getCorrelationId(),
                "Each transaction must have unique correlation ID for tracing");
    }

    // ========== CRITICAL: Logging Verification ==========

    @Test
    @DisplayName("🔴 CRITICAL: Failed transactions must be logged with WARN level")
    void testLogging_FailedTransactionLogged() {
        String idemKey = "idem-log-fail";
        testAccount.setBalance(new BigDecimal("50.00"));
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("WITHDRAWAL");
        request.setAmount(new BigDecimal("100.00"));
        request.setCategory("OTHER");

        when(transactionRepository.findByAccount_IdAndIdempotencyKeyAndType(
                anyLong(), anyString(), any())).thenReturn(Optional.empty());
        when(fluxaFraudClient.evaluate(any(), any(), any(), any(), any(), any()))
                .thenReturn(new FluxaEvalOutcome.Disabled());
        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));

        try {
            transactionService.createTransaction(100L, request, idemKey);
            fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException e) {
            // Expected
        }

        verify(loggingService).logEvent(
                anyString(),
                eq(LogEvent.LogLevel.WARN),
                anyString(),
                any());
    }

    @Test
    @DisplayName("🔴 CRITICAL: Successful transactions must be logged with transaction ID")
    void testLogging_SuccessfulTransactionLogged() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("DEPOSIT");
        request.setAmount(new BigDecimal("100.00"));
        request.setCategory("OTHER");

        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(12345L);
            return t;
        });
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        transactionService.createTransaction(100L, request, null);

        verify(loggingService).logTransactionEvent(
                any(Transaction.class),
                eq(LogEvent.LogLevel.INFO),
                contains("completed successfully"),
                any());
    }

    // ========== CRITICAL: Multiple Rapid Transactions ==========

    @Test
    @DisplayName("🔴 CRITICAL: Multiple rapid deposits must accumulate correctly")
    void testMultipleTransactions_RapidDeposits() {
        testAccount.setBalance(new BigDecimal("100.00"));
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("DEPOSIT");
        request.setAmount(new BigDecimal("10.00"));
        request.setCategory("OTHER");

        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(System.currentTimeMillis());
            return t;
        });

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        when(accountRepository.save(accountCaptor.capture())).thenReturn(testAccount);

        for (int i = 0; i < 5; i++) {
            transactionService.createTransaction(100L, request, null);
        }

        BigDecimal expectedBalance = new BigDecimal("150.00");
        assertEquals(0, expectedBalance.compareTo(accountCaptor.getValue().getBalance()),
                "Multiple rapid transactions must accumulate correctly");
    }
}
