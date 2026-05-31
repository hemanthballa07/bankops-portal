package com.bankops.portal.service;

import com.bankops.portal.dto.AccountDto;
import com.bankops.portal.dto.CreateAccountRequest;
import com.bankops.portal.dto.UpdateAccountRequest;
import com.bankops.portal.entity.Account;
import com.bankops.portal.entity.Customer;
import com.bankops.portal.repository.AccountRepository;
import com.bankops.portal.repository.CustomerRepository;
import com.bankops.portal.service.AuditEventService;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService Unit Tests")
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private AuditEventService auditEventService;

    @InjectMocks
    private AccountService accountService;

    private Customer testCustomer;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        // Arrange: Set up test data
        testCustomer = Customer.builder()
                .id(1L)
                .firstName("Jane")
                .lastName("Smith")
                .email("jane@example.com")
                .phone("555-5678")
                .build();

        testAccount = Account.builder()
                .id(100L)
                .customer(testCustomer)
                .type(Account.AccountType.CHEQUING)
                .status(Account.AccountStatus.OPEN)
                .balance(BigDecimal.ZERO)
                .overdraftEnabled(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ========== Create Account Tests ==========

    @Test
    @DisplayName("Should successfully create a checking account")
    void testCreateAccount_Checking_Success() {
        // Arrange
        CreateAccountRequest request = new CreateAccountRequest();
        request.setType("CHEQUING");

        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account acc = invocation.getArgument(0);
            acc.setId(100L);
            acc.setCreatedAt(LocalDateTime.now());
            return acc;
        });

        // Act
        AccountDto result = accountService.createAccount(1L, request);

        // Assert
        assertNotNull(result);
        assertEquals("CHEQUING", result.getType());
        assertEquals("OPEN", result.getStatus());
        assertEquals(BigDecimal.ZERO, result.getBalance());
        assertFalse(result.getOverdraftEnabled());
        assertEquals(1L, result.getCustomerId());

        // Verify account was saved
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        Account savedAccount = accountCaptor.getValue();
        assertEquals(Account.AccountType.CHEQUING, savedAccount.getType());
        assertEquals(Account.AccountStatus.OPEN, savedAccount.getStatus());
    }

    @Test
    @DisplayName("Should successfully create a savings account")
    void testCreateAccount_Savings_Success() {
        // Arrange
        CreateAccountRequest request = new CreateAccountRequest();
        request.setType("SAVINGS");

        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account acc = invocation.getArgument(0);
            acc.setId(101L);
            acc.setCreatedAt(LocalDateTime.now());
            return acc;
        });

        // Act
        AccountDto result = accountService.createAccount(1L, request);

        // Assert
        assertNotNull(result);
        assertEquals("SAVINGS", result.getType());
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("Should throw exception when customer not found")
    void testCreateAccount_CustomerNotFound() {
        // Arrange
        CreateAccountRequest request = new CreateAccountRequest();
        request.setType("CHECKING");

        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.createAccount(999L, request));
        assertEquals("Customer not found with id: 999", exception.getMessage());
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception for invalid account type")
    void testCreateAccount_InvalidType() {
        // Arrange
        CreateAccountRequest request = new CreateAccountRequest();
        request.setType("INVALID_TYPE");

        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.createAccount(1L, request));
        assertTrue(exception.getMessage().contains("Invalid account type"));
        verify(accountRepository, never()).save(any());
    }

    // ========== Get Account Tests ==========

    @Test
    @DisplayName("Should successfully get account by ID")
    void testGetAccountById_Success() {
        // Arrange
        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));

        // Act
        AccountDto result = accountService.getAccountById(100L);

        // Assert
        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals("CHEQUING", result.getType());
        verify(accountRepository).findById(100L);
    }

    @Test
    @DisplayName("Should throw exception when account not found by ID")
    void testGetAccountById_NotFound() {
        // Arrange
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.getAccountById(999L));
        assertEquals("Account not found with id: 999", exception.getMessage());
    }

    @Test
    @DisplayName("Should successfully get accounts by customer ID")
    void testGetAccountsByCustomerId_Success() {
        // Arrange
        Account account2 = Account.builder()
                .id(101L)
                .customer(testCustomer)
                .type(Account.AccountType.SAVINGS)
                .status(Account.AccountStatus.OPEN)
                .balance(new BigDecimal("500.00"))
                .overdraftEnabled(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(accountRepository.findByCustomerId(1L)).thenReturn(Arrays.asList(testAccount, account2));

        // Act
        List<AccountDto> results = accountService.getAccountsByCustomerId(1L);

        // Assert
        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("CHEQUING", results.get(0).getType());
        assertEquals("SAVINGS", results.get(1).getType());
    }

    // ========== Update Account Tests ==========

    @Test
    @DisplayName("Should successfully update account status")
    void testUpdateAccount_Status_Success() {
        // Arrange
        UpdateAccountRequest request = new UpdateAccountRequest();
        request.setStatus("CLOSED");

        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        // Act
        AccountDto result = accountService.updateAccount(100L, request);

        // Assert
        assertNotNull(result);
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertEquals(Account.AccountStatus.CLOSED, accountCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("Should successfully enable overdraft")
    void testUpdateAccount_EnableOverdraft_Success() {
        // Arrange
        UpdateAccountRequest request = new UpdateAccountRequest();
        request.setOverdraftEnabled(true);

        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        // Act
        AccountDto result = accountService.updateAccount(100L, request);

        // Assert
        assertNotNull(result);
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertTrue(accountCaptor.getValue().getOverdraftEnabled());
    }

    @Test
    @DisplayName("Should successfully update both status and overdraft")
    void testUpdateAccount_Multiple_Success() {
        // Arrange
        UpdateAccountRequest request = new UpdateAccountRequest();
        request.setStatus("CLOSED");
        request.setOverdraftEnabled(true);

        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        // Act
        AccountDto result = accountService.updateAccount(100L, request);

        // Assert
        assertNotNull(result);
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        Account savedAccount = accountCaptor.getValue();
        assertEquals(Account.AccountStatus.CLOSED, savedAccount.getStatus());
        assertTrue(savedAccount.getOverdraftEnabled());
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent account")
    void testUpdateAccount_NotFound() {
        // Arrange
        UpdateAccountRequest request = new UpdateAccountRequest();
        request.setStatus("CLOSED");

        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.updateAccount(999L, request));
        assertEquals("Account not found with id: 999", exception.getMessage());
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception for invalid status")
    void testUpdateAccount_InvalidStatus() {
        // Arrange
        UpdateAccountRequest request = new UpdateAccountRequest();
        request.setStatus("INVALID_STATUS");

        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.updateAccount(100L, request));
        assertTrue(exception.getMessage().contains("Invalid account status"));
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle update with null values (no changes)")
    void testUpdateAccount_NullValues() {
        // Arrange
        UpdateAccountRequest request = new UpdateAccountRequest();
        request.setStatus(null);
        request.setOverdraftEnabled(null);

        when(accountRepository.findById(100L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        // Act
        AccountDto result = accountService.updateAccount(100L, request);

        // Assert
        assertNotNull(result);
        // Account should still be saved even with no changes
        verify(accountRepository).save(testAccount);
    }
}
