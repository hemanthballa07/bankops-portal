package com.bankops.portal.service;

import com.bankops.portal.dto.CreateTransactionRequest;
import com.bankops.portal.dto.TransactionDto;
import com.bankops.portal.entity.Account;
import com.bankops.portal.entity.Customer;
import com.bankops.portal.entity.Transaction;
import com.bankops.portal.repository.AccountRepository;
import com.bankops.portal.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for withdrawal idempotency and concurrency safety.
 * These tests verify that:
 * 1. Duplicate idempotency keys don't cause double debits
 * 2. Concurrent withdrawals are handled safely with optimistic locking
 * 3. Insufficient funds scenarios don't create transactions
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Withdrawal Idempotency & Concurrency Tests")
class WithdrawalIdempotencyTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Account testAccount;
    private Customer testCustomer;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up
        transactionRepository.deleteAll();
        accountRepository.deleteAll();

        // Create test customer and account
        testCustomer = Customer.builder()
                .firstName("Test")
                .lastName("User")
                .email("test@example.com")
                .phone("555-0000")
                .build();

        testAccount = Account.builder()
                .customer(testCustomer)
                .type(Account.AccountType.CHEQUING)
                .status(Account.AccountStatus.OPEN)
                .balance(new BigDecimal("1000.00"))
                .overdraftEnabled(false)
                .build();

        testAccount = accountRepository.save(testAccount);
    }

    // ========== Idempotency Tests ==========

    @Test
    @DisplayName("🔐 Duplicate idempotency key returns same transaction without double debit")
    void testDuplicateIdempotencyKey_NoDoubleDebit() {
        // Arrange
        String idempotencyKey = UUID.randomUUID().toString();
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("WITHDRAWAL");
        request.setAmount(new BigDecimal("100.00"));
        request.setDescription("ATM withdrawal");
        request.setCategory("OTHER");

        // Act - First withdrawal
        TransactionDto result1 = transactionService.createTransaction(
                testAccount.getId(), request, idempotencyKey);

        // Reload account to get updated balance
        Account accountAfterFirst = accountRepository.findById(testAccount.getId()).orElseThrow();
        BigDecimal balanceAfterFirst = accountAfterFirst.getBalance();

        // Act - Retry with same idempotency key
        TransactionDto result2 = transactionService.createTransaction(
                testAccount.getId(), request, idempotencyKey);

        // Reload account again
        Account accountAfterSecond = accountRepository.findById(testAccount.getId()).orElseThrow();
        BigDecimal balanceAfterSecond = accountAfterSecond.getBalance();

        // Assert
        assertEquals(result1.getId(), result2.getId(), "Same transaction ID must be returned");
        assertEquals(result1.getCorrelationId(), result2.getCorrelationId(), "Same correlation ID");
        assertEquals(new BigDecimal("900.00"), balanceAfterFirst, "Balance after first withdrawal");
        assertEquals(balanceAfterFirst, balanceAfterSecond, "Balance must not change on retry");

        // Verify only one transaction was created
        List<Transaction> transactions = transactionRepository.findByAccountIdOrderByCreatedAtDesc(testAccount.getId());
        assertEquals(1, transactions.size(), "Only one transaction should exist");
    }

    @Test
    @DisplayName("🔐 Withdrawal without idempotency key throws exception")
    void testWithdrawalWithoutIdempotencyKey_ThrowsException() {
        // Arrange
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("WITHDRAWAL");
        request.setAmount(new BigDecimal("50.00"));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.createTransaction(testAccount.getId(), request, null));

        assertTrue(exception.getMessage().contains("Idempotency-Key"),
                "Error message should mention idempotency key requirement");
    }

    @Test
    @DisplayName("🔐 Insufficient funds doesn't create transaction")
    void testInsufficientFunds_NoTransactionCreated() {
        // Arrange
        String idempotencyKey = UUID.randomUUID().toString();
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("WITHDRAWAL");
        request.setAmount(new BigDecimal("2000.00")); // More than balance
        request.setCategory("OTHER");

        BigDecimal balanceBefore = accountRepository.findById(testAccount.getId()).orElseThrow().getBalance();

        // Act & Assert
        assertThrows(IllegalStateException.class,
                () -> transactionService.createTransaction(testAccount.getId(), request, idempotencyKey));

        // Verify balance unchanged
        Account account = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertEquals(balanceBefore, account.getBalance(), "Balance must not change");

        // Verify no transaction created
        List<Transaction> transactions = transactionRepository.findByAccountIdOrderByCreatedAtDesc(testAccount.getId());
        assertEquals(0, transactions.size(), "No transaction should be created");
    }

    // ========== Concurrency Tests ==========

    @Test
    @DisplayName("🔐 Concurrent withdrawals with different keys - only one succeeds when insufficient funds")
    void testConcurrentWithdrawals_OnlyOneSucceeds() throws InterruptedException {
        // Arrange
        testAccount.setBalance(new BigDecimal("100.00"));
        testAccount = accountRepository.save(testAccount);

        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();

        CreateTransactionRequest request1 = new CreateTransactionRequest();
        request1.setType("WITHDRAWAL");
        request1.setAmount(new BigDecimal("80.00"));
        request1.setCategory("OTHER");

        CreateTransactionRequest request2 = new CreateTransactionRequest();
        request2.setType("WITHDRAWAL");
        request2.setAmount(new BigDecimal("80.00"));
        request2.setCategory("OTHER");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicReference<TransactionDto> successfulTxn = new AtomicReference<>();

        // Act - Fire two concurrent withdrawals
        executor.submit(() -> {
            try {
                TransactionDto result = transactionService.createTransaction(testAccount.getId(), request1, key1);
                successCount.incrementAndGet();
                successfulTxn.set(result);
            } catch (Exception e) {
                failureCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                TransactionDto result = transactionService.createTransaction(testAccount.getId(), request2, key2);
                successCount.incrementAndGet();
                successfulTxn.set(result);
            } catch (Exception e) {
                failureCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });

        latch.await();
        executor.shutdown();

        // Assert
        assertEquals(1, successCount.get(), "Exactly one withdrawal should succeed");
        assertEquals(1, failureCount.get(), "Exactly one withdrawal should fail");

        // Verify final balance
        Account finalAccount = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertEquals(new BigDecimal("20.00"), finalAccount.getBalance(),
                "Final balance should be 100 - 80 = 20");

        // Verify only one transaction exists
        List<Transaction> transactions = transactionRepository.findByAccountIdOrderByCreatedAtDesc(testAccount.getId());
        assertEquals(1, transactions.size(), "Only one transaction should be created");
        assertEquals(Transaction.TransactionStatus.COMPLETED, transactions.get(0).getStatus());
    }

    @Test
    @DisplayName("🔐 Concurrent withdrawals with same idempotency key - no double debit")
    void testConcurrentWithdrawals_SameIdempotencyKey_NoDoubleDebit() throws InterruptedException {
        // Arrange
        String sharedKey = UUID.randomUUID().toString();

        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("WITHDRAWAL");
        request.setAmount(new BigDecimal("100.00"));
        request.setCategory("OTHER");

        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger successCount = new AtomicInteger(0);

        // Act - Fire three concurrent requests with same idempotency key
        for (int i = 0; i < 3; i++) {
            executor.submit(() -> {
                try {
                    transactionService.createTransaction(testAccount.getId(), request, sharedKey);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // May fail due to optimistic locking, but that's OK
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Assert
        assertTrue(successCount.get() >= 1, "At least one request should succeed");

        // Verify balance debited only once
        Account finalAccount = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertEquals(new BigDecimal("900.00"), finalAccount.getBalance(),
                "Balance should be debited exactly once");

        // Verify only one transaction exists
        List<Transaction> transactions = transactionRepository.findByAccountIdOrderByCreatedAtDesc(testAccount.getId());
        assertEquals(1, transactions.size(), "Only one transaction should be created");
    }
}
