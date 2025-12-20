package com.bankops.portal.service;

import com.bankops.portal.dto.CreateTransactionRequest;
import com.bankops.portal.dto.TransactionDto;
import com.bankops.portal.entity.Account;
import com.bankops.portal.entity.Customer;
import com.bankops.portal.entity.Transaction;
import com.bankops.portal.repository.AccountRepository;
import com.bankops.portal.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {
    
    @Mock
    private TransactionRepository transactionRepository;
    
    @Mock
    private AccountRepository accountRepository;
    
    @Mock
    private LoggingService loggingService;
    
    @InjectMocks
    private TransactionService transactionService;
    
    private Customer customer;
    private Account account;
    
    @BeforeEach
    void setUp() {
        customer = Customer.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .phone("123-456-7890")
                .build();
        
        account = Account.builder()
                .id(1L)
                .customer(customer)
                .type(Account.AccountType.CHEQUING)
                .status(Account.AccountStatus.OPEN)
                .balance(new BigDecimal("100.00"))
                .overdraftEnabled(false)
                .build();
    }
    
    @Test
    void testDeposit_IncreasesBalance() {
        // Given
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("DEPOSIT");
        request.setAmount(new BigDecimal("50.00"));
        
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(100L);
            return t;
        });
        when(accountRepository.save(any(Account.class))).thenReturn(account);
        doNothing().when(loggingService).logEvent(anyString(), any(), anyString(), any());
        doNothing().when(loggingService).logTransactionEvent(any(), any(), anyString(), any());
        
        // When
        TransactionDto result = transactionService.createTransaction(1L, request);
        
        // Then
        assertNotNull(result);
        assertNotNull(result.getCorrelationId());
        assertEquals("DEPOSIT", result.getType());
        assertEquals(new BigDecimal("50.00"), result.getAmount());
        assertEquals("COMPLETED", result.getStatus());
        
        // Verify balance was updated
        verify(accountRepository, times(1)).save(argThat(acc -> 
            acc.getBalance().compareTo(new BigDecimal("150.00")) == 0
        ));
        
        // Verify logging was called
        verify(loggingService, atLeastOnce()).logEvent(anyString(), any(), anyString(), any());
    }
    
    @Test
    void testWithdrawal_FailsWhenInsufficientFundsAndOverdraftDisabled() {
        // Given
        account.setBalance(new BigDecimal("100.00"));
        account.setOverdraftEnabled(false);
        
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("WITHDRAWAL");
        request.setAmount(new BigDecimal("150.00"));
        
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        
        // When/Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            transactionService.createTransaction(1L, request);
        });
        
        assertTrue(exception.getMessage().contains("Insufficient funds"));
        
        // Verify transaction was not saved
        verify(transactionRepository, never()).save(any());
        
        // Verify balance was not updated
        verify(accountRepository, never()).save(any(Account.class));
        
        // Verify error was logged
        verify(loggingService).logEvent(anyString(), eq(com.bankops.portal.entity.LogEvent.LogLevel.WARN), 
            contains("Insufficient funds"), any());
    }
    
    @Test
    void testWithdrawal_SucceedsWhenOverdraftEnabled() {
        // Given
        account.setBalance(new BigDecimal("100.00"));
        account.setOverdraftEnabled(true);
        
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("WITHDRAWAL");
        request.setAmount(new BigDecimal("150.00"));
        
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(100L);
            return t;
        });
        when(accountRepository.save(any(Account.class))).thenReturn(account);
        doNothing().when(loggingService).logEvent(anyString(), any(), anyString(), any());
        doNothing().when(loggingService).logTransactionEvent(any(), any(), anyString(), any());
        
        // When
        TransactionDto result = transactionService.createTransaction(1L, request);
        
        // Then
        assertNotNull(result);
        assertEquals("WITHDRAWAL", result.getType());
        assertEquals("COMPLETED", result.getStatus());
        
        // Verify balance was updated (negative balance allowed with overdraft)
        verify(accountRepository, times(1)).save(argThat(acc -> 
            acc.getBalance().compareTo(new BigDecimal("-50.00")) == 0
        ));
    }
    
    @Test
    void testWithdrawal_SucceedsWhenSufficientFunds() {
        // Given
        account.setBalance(new BigDecimal("100.00"));
        account.setOverdraftEnabled(false);
        
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("WITHDRAWAL");
        request.setAmount(new BigDecimal("50.00"));
        
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(100L);
            return t;
        });
        when(accountRepository.save(any(Account.class))).thenReturn(account);
        doNothing().when(loggingService).logEvent(anyString(), any(), anyString(), any());
        doNothing().when(loggingService).logTransactionEvent(any(), any(), anyString(), any());
        
        // When
        TransactionDto result = transactionService.createTransaction(1L, request);
        
        // Then
        assertNotNull(result);
        assertEquals("WITHDRAWAL", result.getType());
        assertEquals("COMPLETED", result.getStatus());
        
        // Verify balance was updated
        verify(accountRepository, times(1)).save(argThat(acc -> 
            acc.getBalance().compareTo(new BigDecimal("50.00")) == 0
        ));
    }
    
    @Test
    void testCorrelationId_IsGeneratedAndReturned() {
        // Given
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("DEPOSIT");
        request.setAmount(new BigDecimal("25.00"));
        
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(100L);
            return t;
        });
        when(accountRepository.save(any(Account.class))).thenReturn(account);
        doNothing().when(loggingService).logEvent(anyString(), any(), anyString(), any());
        doNothing().when(loggingService).logTransactionEvent(any(), any(), anyString(), any());
        
        // When
        TransactionDto result = transactionService.createTransaction(1L, request);
        
        // Then
        assertNotNull(result.getCorrelationId());
        assertFalse(result.getCorrelationId().isEmpty());
        // Verify it's a UUID format (36 characters with hyphens)
        assertEquals(36, result.getCorrelationId().length());
        assertTrue(result.getCorrelationId().contains("-"));
    }
    
    @Test
    void testTransaction_FailsOnClosedAccount() {
        // Given
        account.setStatus(Account.AccountStatus.CLOSED);
        
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("DEPOSIT");
        request.setAmount(new BigDecimal("50.00"));
        
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        
        // When/Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            transactionService.createTransaction(1L, request);
        });
        
        assertTrue(exception.getMessage().contains("closed account"));
    }
    
    @Test
    void testTransaction_FailsOnNegativeAmount() {
        // Given
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType("DEPOSIT");
        request.setAmount(new BigDecimal("-10.00"));
        
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        
        // When/Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            transactionService.createTransaction(1L, request);
        });
        
        assertTrue(exception.getMessage().contains("must be greater than 0"));
    }
}

