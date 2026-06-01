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
import com.bankops.portal.repository.LogEventRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService Unit Tests")
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LogEventRepository logEventRepository;

    @Mock
    private LoggingService loggingService;

    @Mock
    private AuditEventService auditEventService;

    @Mock
    private FluxaFraudClient fluxaFraudClient;

    @Mock
    private CaseService caseService;

    // FluxaProperties is a record (final class) — use a real instance with Fluxa disabled
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
                auditEventService, fluxaFraudClient, fluxaProperties, caseService);
    }

    // ========== Successful Transaction Tests ==========

    @Test
    @DisplayName("Should successfully create a deposit transaction")
    void testCreateDeposit_Success() {
        // Arrange
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("DEPOSIT");
        request.setAmount(new BigDecimal("500.00"));
        request.setDescription("Paycheck deposit");
        request.setCategory("OTHER");

        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(1L);
            return t;
        });
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        // Act
        TransactionDto result = transactionService.createTransaction(100L, request, null);

        // Assert
        assertNotNull(result);
        assertEquals("DEPOSIT", result.getType());
        assertEquals(new BigDecimal("500.00"), result.getAmount());
        assertEquals("Paycheck deposit", result.getDescription());
        assertEquals("COMPLETED", result.getStatus());
        assertNotNull(result.getCorrelationId());

        // Verify balance was updated
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository, times(1)).save(accountCaptor.capture());
        assertEquals(new BigDecimal("1500.00"), accountCaptor.getValue().getBalance());

        // Verify transaction was saved twice: once as PENDING, once as COMPLETED
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Should successfully create a withdrawal transaction with sufficient balance")
    void testCreateWithdrawal_Success() {
        // Arrange
        String idemKey = "idem-withdraw-300";
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("WITHDRAWAL");
        request.setAmount(new BigDecimal("300.00"));
        request.setDescription("ATM withdrawal");
        request.setCategory("OTHER");

        when(transactionRepository.findByAccount_IdAndIdempotencyKeyAndType(
                anyLong(), anyString(), any())).thenReturn(Optional.empty());
        when(fluxaFraudClient.evaluate(any(), any(), any(), any(), any(), any()))
                .thenReturn(new FluxaEvalOutcome.Disabled());
        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(2L);
            return t;
        });
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        // Act
        TransactionDto result = transactionService.createTransaction(100L, request, idemKey);

        // Assert
        assertNotNull(result);
        assertEquals("WITHDRAWAL", result.getType());
        assertEquals(new BigDecimal("300.00"), result.getAmount());

        // Verify balance was updated
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository, times(1)).save(accountCaptor.capture());
        assertEquals(new BigDecimal("700.00"), accountCaptor.getValue().getBalance());
    }

    @Test
    @DisplayName("Should forward request merchant to Fluxa on withdrawal")
    void testWithdrawal_ForwardsMerchantToFluxa() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("WITHDRAWAL");
        request.setAmount(new BigDecimal("100.00"));
        request.setMerchant("SilkRoadBazaar");

        when(transactionRepository.findByAccount_IdAndIdempotencyKeyAndType(
                anyLong(), anyString(), any())).thenReturn(Optional.empty());
        when(fluxaFraudClient.evaluate(any(), any(), any(), any(), any(), any()))
                .thenReturn(new FluxaEvalOutcome.Disabled());
        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(20L);
            return t;
        });
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        transactionService.createTransaction(100L, request, "idem-merchant");

        ArgumentCaptor<String> merchantCaptor = ArgumentCaptor.forClass(String.class);
        verify(fluxaFraudClient).evaluate(any(), any(), any(), any(),
                merchantCaptor.capture(), any());
        assertEquals("SilkRoadBazaar", merchantCaptor.getValue());
    }

    @Test
    @DisplayName("Should fall back to UNSPECIFIED merchant when request omits it")
    void testWithdrawal_NullMerchant_FallsBackToUnspecified() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("WITHDRAWAL");
        request.setAmount(new BigDecimal("100.00"));
        // merchant intentionally left null

        when(transactionRepository.findByAccount_IdAndIdempotencyKeyAndType(
                anyLong(), anyString(), any())).thenReturn(Optional.empty());
        when(fluxaFraudClient.evaluate(any(), any(), any(), any(), any(), any()))
                .thenReturn(new FluxaEvalOutcome.Disabled());
        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(21L);
            return t;
        });
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        transactionService.createTransaction(100L, request, "idem-null-merchant");

        ArgumentCaptor<String> merchantCaptor = ArgumentCaptor.forClass(String.class);
        verify(fluxaFraudClient).evaluate(any(), any(), any(), any(),
                merchantCaptor.capture(), any());
        assertEquals("UNSPECIFIED", merchantCaptor.getValue());
    }

    @Test
    @DisplayName("Should successfully create withdrawal with overdraft enabled")
    void testCreateWithdrawal_WithOverdraft_Success() {
        // Arrange
        testAccount.setOverdraftEnabled(true);
        String idemKey = "idem-overdraft";
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("WITHDRAWAL");
        request.setAmount(new BigDecimal("1200.00")); // More than balance
        request.setCategory("OTHER");

        when(transactionRepository.findByAccount_IdAndIdempotencyKeyAndType(
                anyLong(), anyString(), any())).thenReturn(Optional.empty());
        when(fluxaFraudClient.evaluate(any(), any(), any(), any(), any(), any()))
                .thenReturn(new FluxaEvalOutcome.Disabled());
        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(3L);
            return t;
        });
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        // Act
        TransactionDto result = transactionService.createTransaction(100L, request, idemKey);

        // Assert
        assertNotNull(result);
        assertEquals("WITHDRAWAL", result.getType());

        // Verify balance went negative
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository, times(1)).save(accountCaptor.capture());
        assertEquals(new BigDecimal("-200.00"), accountCaptor.getValue().getBalance());
    }

    // ========== Validation Failure Tests ==========

    @Test
    @DisplayName("Should throw exception when account not found")
    void testCreateTransaction_AccountNotFound() {
        // Arrange
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("DEPOSIT");
        request.setAmount(new BigDecimal("100.00"));

        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.createTransaction(999L, request, null));
        assertEquals("Account not found with id: 999", exception.getMessage());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when account is closed")
    void testCreateTransaction_ClosedAccount() {
        // Arrange
        testAccount.setStatus(Account.AccountStatus.CLOSED);
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("DEPOSIT");
        request.setAmount(new BigDecimal("100.00"));

        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));

        // Act & Assert
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> transactionService.createTransaction(100L, request, null));
        assertEquals("Cannot process transaction on closed account", exception.getMessage());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception for invalid transaction type")
    void testCreateTransaction_InvalidType() {
        // Arrange — invalid type rejected before any repository call
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("INVALID_TYPE");
        request.setAmount(new BigDecimal("100.00"));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.createTransaction(100L, request, null));
        assertTrue(exception.getMessage().contains("Invalid transaction type"));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception for zero amount")
    void testCreateTransaction_ZeroAmount() {
        // Arrange
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("DEPOSIT");
        request.setAmount(BigDecimal.ZERO);

        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.createTransaction(100L, request, null));
        assertEquals("Transaction amount must be greater than 0", exception.getMessage());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception for negative amount")
    void testCreateTransaction_NegativeAmount() {
        // Arrange
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("DEPOSIT");
        request.setAmount(new BigDecimal("-50.00"));

        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.createTransaction(100L, request, null));
        assertEquals("Transaction amount must be greater than 0", exception.getMessage());
        verify(transactionRepository, never()).save(any());
    }

    // ========== Edge Case Tests ==========

    @Test
    @DisplayName("Should throw exception for insufficient funds without overdraft")
    void testCreateWithdrawal_InsufficientFunds() {
        // Arrange
        String idemKey = "idem-insufficient";
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("WITHDRAWAL");
        request.setAmount(new BigDecimal("1500.00")); // More than balance
        request.setCategory("OTHER");

        when(transactionRepository.findByAccount_IdAndIdempotencyKeyAndType(
                anyLong(), anyString(), any())).thenReturn(Optional.empty());
        when(fluxaFraudClient.evaluate(any(), any(), any(), any(), any(), any()))
                .thenReturn(new FluxaEvalOutcome.Disabled());
        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));

        // Act & Assert
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> transactionService.createTransaction(100L, request, idemKey));
        assertEquals("Insufficient funds. Overdraft not enabled.", exception.getMessage());
        verify(transactionRepository, never()).save(any());
        verify(loggingService).logEvent(anyString(), eq(LogEvent.LogLevel.WARN), anyString(), any());
    }

    @Test
    @DisplayName("Should handle exact balance withdrawal")
    void testCreateWithdrawal_ExactBalance() {
        // Arrange
        String idemKey = "idem-exact";
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("WITHDRAWAL");
        request.setAmount(new BigDecimal("1000.00")); // Exact balance
        request.setCategory("OTHER");

        when(transactionRepository.findByAccount_IdAndIdempotencyKeyAndType(
                anyLong(), anyString(), any())).thenReturn(Optional.empty());
        when(fluxaFraudClient.evaluate(any(), any(), any(), any(), any(), any()))
                .thenReturn(new FluxaEvalOutcome.Disabled());
        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(4L);
            return t;
        });
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        // Act
        TransactionDto result = transactionService.createTransaction(100L, request, idemKey);

        // Assert
        assertNotNull(result);
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository, times(1)).save(accountCaptor.capture());
        assertEquals(0, BigDecimal.ZERO.compareTo(accountCaptor.getValue().getBalance()));
    }

    @Test
    @DisplayName("Should default to OTHER category for invalid category")
    void testCreateTransaction_InvalidCategory_DefaultsToOther() {
        // Arrange
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("DEPOSIT");
        request.setAmount(new BigDecimal("100.00"));
        request.setCategory("INVALID_CATEGORY");

        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(5L);
            return t;
        });
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        // Act
        TransactionDto result = transactionService.createTransaction(100L, request, null);

        // Assert
        assertNotNull(result);
        assertEquals("OTHER", result.getCategory());
    }

    @Test
    @DisplayName("Should handle null category by defaulting to OTHER")
    void testCreateTransaction_NullCategory_DefaultsToOther() {
        // Arrange
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("DEPOSIT");
        request.setAmount(new BigDecimal("100.00"));
        request.setCategory(null);

        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(6L);
            return t;
        });
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        // Act
        TransactionDto result = transactionService.createTransaction(100L, request, null);

        // Assert
        assertNotNull(result);
        assertEquals("OTHER", result.getCategory());
    }

    @Test
    @DisplayName("Should correctly parse valid category")
    void testCreateTransaction_ValidCategory() {
        // Arrange
        String idemKey = "idem-groceries";
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("WITHDRAWAL");
        request.setAmount(new BigDecimal("50.00"));
        request.setCategory("GROCERIES");

        when(transactionRepository.findByAccount_IdAndIdempotencyKeyAndType(
                anyLong(), anyString(), any())).thenReturn(Optional.empty());
        when(fluxaFraudClient.evaluate(any(), any(), any(), any(), any(), any()))
                .thenReturn(new FluxaEvalOutcome.Disabled());
        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(7L);
            return t;
        });
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        // Act
        TransactionDto result = transactionService.createTransaction(100L, request, idemKey);

        // Assert
        assertNotNull(result);
        assertEquals("GROCERIES", result.getCategory());
    }

    @Test
    @DisplayName("Should handle very small decimal amounts")
    void testCreateTransaction_SmallDecimal() {
        // Arrange
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("DEPOSIT");
        request.setAmount(new BigDecimal("0.01")); // 1 cent
        request.setCategory("OTHER");

        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(8L);
            return t;
        });
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        // Act
        TransactionDto result = transactionService.createTransaction(100L, request, null);

        // Assert
        assertNotNull(result);
        assertEquals(new BigDecimal("0.01"), result.getAmount());
    }
}
