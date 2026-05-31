package com.bankops.portal.service;

import com.bankops.portal.dto.CreateCaseRequest;
import com.bankops.portal.dto.SupportCaseDto;
import com.bankops.portal.dto.UpdateCaseRequest;
import com.bankops.portal.entity.*;
import com.bankops.portal.repository.*;
import com.bankops.portal.sla.SlaService;
import com.bankops.portal.statemachine.CaseState;
import com.bankops.portal.statemachine.CaseStateMachine;
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

    @Mock
    private CaseNoteRepository caseNoteRepository;

    @Mock
    private AuditEventService auditEventService;

    @Mock
    private SlaService slaService;

    @Mock
    private CaseStateMachine stateMachine;

    @Mock
    private com.bankops.portal.assignment.AssignmentService assignmentService;

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
        when(stateMachine.generateCorrelationId()).thenReturn("case-test-123");
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
        assertEquals("NEW", result.getStatus()); // New cases start in NEW state
        assertEquals("HIGH", result.getSeverity());
        assertEquals("Transaction issue reported", result.getSummary());
        assertNotNull(result.getCorrelationId());

        verify(caseRepository).save(any(SupportCase.class));
        verify(stateMachine).generateCorrelationId();
    }

    @Test
    void testCreateCase_WithoutLinkedEntities() {
        // Given
        CreateCaseRequest request = new CreateCaseRequest();
        request.setCustomerId(1L);
        request.setSummary("General inquiry");

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(stateMachine.generateCorrelationId()).thenReturn("case-test-456");
        when(caseRepository.save(any(SupportCase.class))).thenAnswer(invocation -> {
            SupportCase c = invocation.getArgument(0);
            c.setId(101L);
            return c;
        });

        // When
        SupportCaseDto result = caseService.createCase(request);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getCustomerId());
        assertNull(result.getAccountId());
        assertNull(result.getTransactionId());
        assertEquals("NEW", result.getStatus());
        assertEquals("MEDIUM", result.getSeverity()); // Default severity

        verify(caseRepository).save(any(SupportCase.class));
    }

    @Test
    void testStatusTransition_NewToAssigned() {
        // Given
        SupportCase supportCase = SupportCase.builder()
                .id(1L)
                .customer(customer)
                .state(CaseState.NEW)
                .severity(SupportCase.CaseSeverity.HIGH)
                .summary("Test case")
                .correlationId("case-123")
                .build();

        UpdateCaseRequest request = new UpdateCaseRequest();
        request.setStatus("ASSIGNED");

        when(caseRepository.findById(1L)).thenReturn(Optional.of(supportCase));
        when(stateMachine.executeTransitionToState(any(), eq(CaseState.ASSIGNED), any(), any(), any()))
                .thenReturn(CaseState.ASSIGNED);
        when(caseRepository.save(any(SupportCase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        SupportCaseDto result = caseService.updateCaseStatus(1L, request);

        // Then
        assertNotNull(result);
        verify(stateMachine).executeTransitionToState(
                eq(supportCase),
                eq(CaseState.ASSIGNED),
                eq("SYSTEM"),
                eq("case-123"),
                anyString());
        verify(caseRepository).save(any(SupportCase.class));
    }

    @Test
    void testStatusTransition_AssignedToInProgress() {
        // Given
        SupportCase supportCase = SupportCase.builder()
                .id(1L)
                .customer(customer)
                .state(CaseState.ASSIGNED)
                .severity(SupportCase.CaseSeverity.HIGH)
                .summary("Test case")
                .assignedTo("agent1")
                .correlationId("case-123")
                .build();

        UpdateCaseRequest request = new UpdateCaseRequest();
        request.setStatus("IN_PROGRESS");

        when(caseRepository.findById(1L)).thenReturn(Optional.of(supportCase));
        when(stateMachine.executeTransitionToState(any(), eq(CaseState.IN_PROGRESS), any(), any(), any()))
                .thenReturn(CaseState.IN_PROGRESS);
        when(caseRepository.save(any(SupportCase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        SupportCaseDto result = caseService.updateCaseStatus(1L, request);

        // Then
        assertNotNull(result);
        verify(stateMachine).executeTransitionToState(
                eq(supportCase),
                eq(CaseState.IN_PROGRESS),
                eq("SYSTEM"),
                eq("case-123"),
                anyString());
    }

    @Test
    void testStatusTransition_InProgressToResolved() {
        // Given
        SupportCase supportCase = SupportCase.builder()
                .id(1L)
                .customer(customer)
                .state(CaseState.IN_PROGRESS)
                .severity(SupportCase.CaseSeverity.HIGH)
                .summary("Test case")
                .assignedTo("agent1")
                .correlationId("case-123")
                .resolution("Issue fixed")
                .build();

        UpdateCaseRequest request = new UpdateCaseRequest();
        request.setStatus("RESOLVED");

        when(caseRepository.findById(1L)).thenReturn(Optional.of(supportCase));
        when(stateMachine.executeTransitionToState(any(), eq(CaseState.RESOLVED), any(), any(), any()))
                .thenReturn(CaseState.RESOLVED);
        when(caseRepository.save(any(SupportCase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        SupportCaseDto result = caseService.updateCaseStatus(1L, request);

        // Then
        assertNotNull(result);
        verify(stateMachine).executeTransitionToState(
                eq(supportCase),
                eq(CaseState.RESOLVED),
                eq("SYSTEM"),
                eq("case-123"),
                anyString());
    }

    @Test
    void testStatusTransition_InvalidTransition_ThrowsException() {
        // Given
        SupportCase supportCase = SupportCase.builder()
                .id(1L)
                .customer(customer)
                .state(CaseState.CLOSED)
                .severity(SupportCase.CaseSeverity.HIGH)
                .summary("Test case")
                .correlationId("case-123")
                .build();

        UpdateCaseRequest request = new UpdateCaseRequest();
        request.setStatus("NEW");

        when(caseRepository.findById(1L)).thenReturn(Optional.of(supportCase));
        when(stateMachine.executeTransitionToState(any(), eq(CaseState.NEW), any(), any(), any()))
                .thenThrow(new IllegalStateException("Cannot transition from terminal state CLOSED"));

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            caseService.updateCaseStatus(1L, request);
        });

        verify(stateMachine).executeTransitionToState(any(), eq(CaseState.NEW), any(), any(), any());
    }
}
