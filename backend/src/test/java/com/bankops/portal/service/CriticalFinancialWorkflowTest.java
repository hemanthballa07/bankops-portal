package com.bankops.portal.service;

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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Critical financial workflow tests focusing on untested branches
 * and production bug prevention scenarios.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Critical Financial Workflow Tests")
class CriticalFinancialWorkflowTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
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
    }

    // ========== CRITICAL: Transaction Status Transitions ==========

    @Test
    @DisplayName("🔴 CRITICAL: Transaction must transition from PENDING to COMPLETED")
    void testTransactionStatusTransition_PendingToCompleted() {
        // Arrange
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("DEPOSIT");
        request.setAmount(new BigDecimal("100.00"));
        request.setCategory("OTHER");

        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));

        // Capture all saves to verify status transitions
        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        when(transactionRepository.save(transactionCaptor.capture())).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            if (t.getId() == null)
                t.setId(1L);
            return t;
        });
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        // Act
        TransactionDto result = transactionService.createTransaction(100L, request, null);

        // Assert
        assertEquals("COMPLETED", result.getStatus());

        // Verify status progression: PENDING → COMPLETED
        verify(transactionRepository, times(2)).save(any(Transaction.class));
        var savedTransactions = transactionCaptor.getAllValues();
        assertEquals(Transaction.TransactionStatus.PENDING, savedTransactions.get(0).getStatus(),
                "First save must be PENDING");
        assertEquals(Transaction.TransactionStatus.COMPLETED, savedTransactions.get(1).getStatus(),
                "Second save must be COMPLETED");
    }

    @Test
    @DisplayName("🔴 CRITICAL: Failed transaction must not update balance if status not COMPLETED")
    void testFailedTransaction_DoesNotUpdateBalance() {
        // Arrange
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("WITHDRAWAL");
        request.setAmount(new BigDecimal("2000.00")); // More than balance
        request.setCategory("OTHER");

        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));

        // Act & Assert
        assertThrows(IllegalStateException.class,
                () -> transactionService.createTransaction(100L, request, null));

        // Verify balance was NOT updated
        verify(accountRepository, never()).save(any(Account.class));
        // Verify transaction was NOT saved
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    // ========== CRITICAL: Balance Precision Edge Cases ==========

    @Test
    @DisplayName("🔴 CRITICAL: High precision decimals must not lose cents")
    void testBalancePrecision_HighDecimalPlaces() {
        // Arrange
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

        // Act
        transactionService.createTransaction(100L, request, null);

        // Assert - verify exact precision maintained
        BigDecimal expectedBalance = new BigDecimal("101.000000");
        assertEquals(0, expectedBalance.compareTo(accountCaptor.getValue().getBalance()),
                "Balance precision must be maintained to 6 decimal places");
    }

    @Test
    @DisplayName("🔴 CRITICAL: Very large amounts must not overflow")
    void testBalancePrecision_VeryLargeAmounts() {
        // Arrange
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

        // Act
        TransactionDto result = transactionService.createTransaction(100L, request, null);

        // Assert
        assertNotNull(result);
        assertEquals(0, largeAmount.compareTo(accountCaptor.getValue().getBalance()),
                "Large amounts must be handled without overflow");
    }

    @Test
    @DisplayName("🔴 CRITICAL: Withdrawal leaving exactly zero balance must succeed")
    void testBalancePrecision_ExactZeroBalance() {
        // Arrange
        testAccount.setBalance(new BigDecimal("50.00"));
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("WITHDRAWAL");
        request.setAmount(new BigDecimal("50.00"));
        request.setCategory("OTHER");

        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(1L);
            return t;
        });

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        when(accountRepository.save(accountCaptor.capture())).thenReturn(testAccount);

        // Act
        TransactionDto result = transactionService.createTransaction(100L, request, null);

        // Assert
        assertNotNull(result);
        assertEquals(0, BigDecimal.ZERO.compareTo(accountCaptor.getValue().getBalance()),
                "Exact zero balance must be allowed");
    }

    // ========== CRITICAL: Null Overdraft Handling ==========

    @Test
    @DisplayName("🔴 CRITICAL: Null overdraft must be treated as disabled")
    void testOverdraft_NullTreatedAsDisabled() {
        // Arrange
        testAccount.setOverdraftEnabled(null); // Explicitly null
        testAccount.setBalance(new BigDecimal("50.00"));

        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("WITHDRAWAL");
        request.setAmount(new BigDecimal("100.00"));
        request.setCategory("OTHER");

        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));

        // Act & Assert
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> transactionService.createTransaction(100L, request, null));
        assertTrue(exception.getMessage().contains("Insufficient funds"),
                "Null overdraft must prevent negative balance");
    }

    @Test
    @DisplayName("🔴 CRITICAL: False overdraft must prevent negative balance")
    void testOverdraft_FalsePreventNegativeBalance() {
        // Arrange
        testAccount.setOverdraftEnabled(false);
        testAccount.setBalance(new BigDecimal("50.00"));

        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("WITHDRAWAL");
        request.setAmount(new BigDecimal("50.01")); // Just over balance
        request.setCategory("OTHER");

        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));

        // Act & Assert
        assertThrows(IllegalStateException.class,
                () -> transactionService.createTransaction(100L, request, null),
                "Even $0.01 overdraft must be prevented when disabled");
    }

    // ========== CRITICAL: Correlation ID Handling ==========

    @Test
    @DisplayName("🔴 CRITICAL: Each transaction must have unique correlation ID")
    void testCorrelationId_UniquePerTransaction() {
        // Arrange
        CreateTransactionRequest request1 = new CreateTransactionRequest();
        request1.setType("DEPOSIT");
        request1.setAmount(new BigDecimal("10.00"));
        request1.setCategory("OTHER");

        CreateTransactionRequest request2 = new CreateTransactionRequest();
        request2.setType("DEPOSIT");
        request2.setAmount(new BigDecimal("20.00"));
        request2.setCategory("OTHER");

        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        when(transactionRepository.save(transactionCaptor.capture())).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            if (t.getId() == null)
                t.setId(System.currentTimeMillis()); // Unique IDs
            return t;
        });
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        // Act
        TransactionDto result1 = transactionService.createTransaction(100L, request1, null);
        TransactionDto result2 = transactionService.createTransaction(100L, request2, null);

        // Assert
        assertNotEquals(result1.getCorrelationId(), result2.getCorrelationId(),
                "Each transaction must have unique correlation ID for tracing");
    }

    // ========== CRITICAL: Logging Verification ==========

    @Test
    @DisplayName("🔴 CRITICAL: Failed transactions must be logged with WARN level")
    void testLogging_FailedTransactionLogged() {
        // Arrange
        testAccount.setBalance(new BigDecimal("50.00"));
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("WITHDRAWAL");
        request.setAmount(new BigDecimal("100.00"));
        request.setCategory("OTHER");

        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));

        // Act
        try {
            transactionService.createTransaction(100L, request, null);
            fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException e) {
            // Expected
        }

        // Assert - verify WARN log was created
        verify(loggingService).logEvent(
                anyString(),
                eq(LogEvent.LogLevel.WARN),
                contains("Insufficient funds"),
                any());
    }

    @Test
    @DisplayName("🔴 CRITICAL: Successful transactions must be logged with transaction ID")
    void testLogging_SuccessfulTransactionLogged() {
        // Arrange
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

        // Act
        transactionService.createTransaction(100L, request, null);

        // Assert - verify success log includes transaction ID
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
        // Arrange
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

        // Act - simulate 5 rapid deposits
        for (int i = 0; i < 5; i++) {
            transactionService.createTransaction(100L, request, null);
        }

        // Assert - final balance should be 100 + (5 * 10) = 150
        BigDecimal expectedBalance = new BigDecimal("150.00");
        assertEquals(0, expectedBalance.compareTo(accountCaptor.getValue().getBalance()),
                "Multiple rapid transactions must accumulate correctly");
    }
}
