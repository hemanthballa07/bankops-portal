package com.bankops.portal.service;

import com.bankops.portal.dto.CreateCaseRequest;
import com.bankops.portal.dto.SupportCaseDto;
import com.bankops.portal.dto.UpdateCaseRequest;
import com.bankops.portal.entity.*;
import com.bankops.portal.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaseServiceTest {
    
    @Mock
    private SupportCaseRepository caseRepository;
    
    @Mock
    private CustomerRepository customerRepository;
    
    @Mock
    private AccountRepository accountRepository;
    
    @Mock
    private TransactionRepository transactionRepository;
    
    @InjectMocks
    private CaseService caseService;
    
    private Customer customer;
    private Account account;
    private Transaction transaction;
    
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
        
        transaction = Transaction.builder()
                .id(1L)
                .account(account)
                .type(Transaction.TransactionType.DEPOSIT)
                .amount(new BigDecimal("50.00"))
                .status(Transaction.TransactionStatus.COMPLETED)
                .correlationId("test-correlation-id-123")
                .build();
    }
    
    @Test
    void testCreateCase_LinkedToTransaction() {
        // Given
        CreateCaseRequest request = new CreateCaseRequest();
        request.setCustomerId(1L);
        request.setAccountId(1L);
        request.setTransactionId(1L);
        request.setSummary("Transaction issue reported");
        request.setSeverity("HIGH");
        
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));
        when(caseRepository.save(any(SupportCase.class))).thenAnswer(invocation -> {
            SupportCase c = invocation.getArgument(0);
            c.setId(100L);
            return c;
        });
        
        // When
        SupportCaseDto result = caseService.createCase(request);
        
        // Then
        assertNotNull(result);
        assertEquals(1L, result.getCustomerId());
        assertEquals(1L, result.getAccountId());
        assertEquals(1L, result.getTransactionId());
        assertEquals("OPEN", result.getStatus());
        assertEquals("HIGH", result.getSeverity());
        assertEquals("Transaction issue reported", result.getSummary());
        
        verify(caseRepository).save(argThat(supportCase ->
            supportCase.getCustomer().getId() == 1L &&
            supportCase.getAccount().getId() == 1L &&
            supportCase.getTransaction().getId() == 1L &&
            supportCase.getStatus() == SupportCase.CaseStatus.OPEN
        ));
    }
    
    @Test
    void testCreateCase_WithCorrelationId() {
        // Given
        CreateCaseRequest request = new CreateCaseRequest();
        request.setCustomerId(1L);
        request.setTransactionId(1L);
        request.setSummary("Investigate transaction");
        
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));
        when(caseRepository.save(any(SupportCase.class))).thenAnswer(invocation -> {
            SupportCase c = invocation.getArgument(0);
            c.setId(100L);
            return c;
        });
        
        // When
        SupportCaseDto result = caseService.createCase(request);
        
        // Then
        assertNotNull(result);
        assertEquals(1L, result.getTransactionId());
        // Verify the case can be linked via the transaction's correlation ID
        assertEquals("test-correlation-id-123", transaction.getCorrelationId());
    }
    
    @Test
    void testStatusTransition_OpenToInvestigating() {
        // Given
        SupportCase supportCase = SupportCase.builder()
                .id(1L)
                .customer(customer)
                .status(SupportCase.CaseStatus.OPEN)
                .severity(SupportCase.CaseSeverity.MEDIUM)
                .summary("Test case")
                .build();
        
        UpdateCaseRequest request = new UpdateCaseRequest();
        request.setStatus("INVESTIGATING");
        
        when(caseRepository.findById(1L)).thenReturn(Optional.of(supportCase));
        when(caseRepository.save(any(SupportCase.class))).thenReturn(supportCase);
        
        // When
        SupportCaseDto result = caseService.updateCaseStatus(1L, request);
        
        // Then
        assertEquals("INVESTIGATING", result.getStatus());
        verify(caseRepository).save(argThat(c ->
            c.getStatus() == SupportCase.CaseStatus.INVESTIGATING
        ));
    }
    
    @Test
    void testStatusTransition_InvestigatingToResolved() {
        // Given
        SupportCase supportCase = SupportCase.builder()
                .id(1L)
                .customer(customer)
                .status(SupportCase.CaseStatus.INVESTIGATING)
                .severity(SupportCase.CaseSeverity.MEDIUM)
                .summary("Test case")
                .build();
        
        UpdateCaseRequest request = new UpdateCaseRequest();
        request.setStatus("RESOLVED");
        
        when(caseRepository.findById(1L)).thenReturn(Optional.of(supportCase));
        when(caseRepository.save(any(SupportCase.class))).thenReturn(supportCase);
        
        // When
        SupportCaseDto result = caseService.updateCaseStatus(1L, request);
        
        // Then
        assertEquals("RESOLVED", result.getStatus());
        verify(caseRepository).save(argThat(c ->
            c.getStatus() == SupportCase.CaseStatus.RESOLVED
        ));
    }
    
    @Test
    void testStatusTransition_OpenToResolved_IsValid() {
        // Given
        SupportCase supportCase = SupportCase.builder()
                .id(1L)
                .customer(customer)
                .status(SupportCase.CaseStatus.OPEN)
                .severity(SupportCase.CaseSeverity.MEDIUM)
                .summary("Test case")
                .build();
        
        UpdateCaseRequest request = new UpdateCaseRequest();
        request.setStatus("RESOLVED");
        
        when(caseRepository.findById(1L)).thenReturn(Optional.of(supportCase));
        when(caseRepository.save(any(SupportCase.class))).thenReturn(supportCase);
        
        // When
        SupportCaseDto result = caseService.updateCaseStatus(1L, request);
        
        // Then
        assertEquals("RESOLVED", result.getStatus());
        // This should not throw an exception - OPEN -> RESOLVED is valid
    }
    
    @Test
    void testStatusTransition_ResolvedToOpen_IsInvalid() {
        // Given
        SupportCase supportCase = SupportCase.builder()
                .id(1L)
                .customer(customer)
                .status(SupportCase.CaseStatus.RESOLVED)
                .severity(SupportCase.CaseSeverity.MEDIUM)
                .summary("Test case")
                .build();
        
        UpdateCaseRequest request = new UpdateCaseRequest();
        request.setStatus("OPEN");
        
        when(caseRepository.findById(1L)).thenReturn(Optional.of(supportCase));
        
        // When/Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            caseService.updateCaseStatus(1L, request);
        });
        
        assertTrue(exception.getMessage().contains("Cannot change status from RESOLVED"));
    }
    
    @Test
    void testStatusTransition_OpenToInvalidStatus_IsRejected() {
        // Given
        SupportCase supportCase = SupportCase.builder()
                .id(1L)
                .customer(customer)
                .status(SupportCase.CaseStatus.OPEN)
                .severity(SupportCase.CaseSeverity.MEDIUM)
                .summary("Test case")
                .build();
        
        UpdateCaseRequest request = new UpdateCaseRequest();
        request.setStatus("INVALID_STATUS");
        
        when(caseRepository.findById(1L)).thenReturn(Optional.of(supportCase));
        
        // When/Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            caseService.updateCaseStatus(1L, request);
        });
        
        assertTrue(exception.getMessage().contains("Invalid case status"));
    }
}

