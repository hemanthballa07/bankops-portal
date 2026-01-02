package com.bankops.portal.service;

import com.bankops.portal.dto.CreateCaseRequest;
import com.bankops.portal.dto.SupportCaseDto;
import com.bankops.portal.dto.UpdateCaseRequest;
import com.bankops.portal.dto.CaseNoteDto;
import com.bankops.portal.dto.AddCaseNoteRequest;
import com.bankops.portal.dto.AssignCaseRequest;
import com.bankops.portal.dto.ResolveCaseRequest;
import com.bankops.portal.entity.*;
import com.bankops.portal.repository.*;
import com.bankops.portal.statemachine.CaseState;
import com.bankops.portal.statemachine.CaseStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CaseService {

        private final SupportCaseRepository caseRepository;
        private final CustomerRepository customerRepository;
        private final AccountRepository accountRepository;
        private final TransactionRepository transactionRepository;
        private final CaseNoteRepository caseNoteRepository;
        private final AuditEventService auditEventService;
        private final CaseStateMachine stateMachine;

        @Transactional
        public SupportCaseDto createCase(CreateCaseRequest request) {
                Customer customer = customerRepository.findById(request.getCustomerId())
                                .orElseThrow(
                                                () -> new IllegalArgumentException("Customer not found with id: "
                                                                + request.getCustomerId()));

                Account account = null;
                if (request.getAccountId() != null) {
                        account = accountRepository.findById(request.getAccountId())
                                        .orElseThrow(
                                                        () -> new IllegalArgumentException("Account not found with id: "
                                                                        + request.getAccountId()));
                }

                Transaction transaction = null;
                if (request.getTransactionId() != null) {
                        transaction = transactionRepository.findById(request.getTransactionId())
                                        .orElseThrow(() -> new IllegalArgumentException(
                                                        "Transaction not found with id: "
                                                                        + request.getTransactionId()));
                }

                SupportCase.CaseSeverity severity = SupportCase.CaseSeverity.MEDIUM;
                if (request.getSeverity() != null) {
                        try {
                                severity = SupportCase.CaseSeverity.valueOf(request.getSeverity().toUpperCase());
                        } catch (IllegalArgumentException e) {
                                throw new IllegalArgumentException("Invalid case severity: " + request.getSeverity());
                        }
                }

                String correlationId = stateMachine.generateCorrelationId();

                SupportCase supportCase = SupportCase.builder()
                                .customer(customer)
                                .account(account)
                                .transaction(transaction)
                                .state(CaseState.NEW)
                                .severity(severity)
                                .summary(request.getSummary())
                                .correlationId(correlationId)
                                .build();

                supportCase = caseRepository.save(supportCase);
                return toDto(supportCase);
        }

        public List<SupportCaseDto> getCases(CaseState state, SupportCase.CaseSeverity severity) {
                List<SupportCase> cases = caseRepository.findByStateAndSeverity(state, severity);
                return cases.stream()
                                .map(this::toDto)
                                .collect(Collectors.toList());
        }

        @Transactional
        public SupportCaseDto updateCaseStatus(Long id, UpdateCaseRequest request) {
                SupportCase supportCase = caseRepository.findById(id)
                                .orElseThrow(() -> new IllegalArgumentException("Case not found with id: " + id));

                CaseState oldState = supportCase.getState();

                if (request.getStatus() != null) {
                        CaseState newState;
                        try {
                                newState = CaseState.valueOf(request.getStatus().toUpperCase());
                        } catch (IllegalArgumentException e) {
                                throw new IllegalArgumentException("Invalid case state: " + request.getStatus());
                        }

                        // Use state machine to execute transition
                        CaseState resultState = stateMachine.executeTransitionToState(
                                        supportCase, newState, "SYSTEM",
                                        supportCase.getCorrelationId(), "Status update via API");
                        supportCase.setState(resultState);
                }

                supportCase = caseRepository.save(supportCase);
                return toDto(supportCase);
        }

        // Removed: validation now handled by CaseStateMachine

        private SupportCaseDto toDto(SupportCase supportCase) {
                return SupportCaseDto.builder()
                                .id(supportCase.getId())
                                .customerId(supportCase.getCustomer().getId())
                                .accountId(supportCase.getAccount() != null ? supportCase.getAccount().getId() : null)
                                .transactionId(supportCase.getTransaction() != null
                                                ? supportCase.getTransaction().getId()
                                                : null)
                                .status(supportCase.getState().name())
                                .severity(supportCase.getSeverity().name())
                                .summary(supportCase.getSummary())
                                .createdAt(supportCase.getCreatedAt())
                                .updatedAt(supportCase.getUpdatedAt())
                                .assignedTo(supportCase.getAssignedTo())
                                .correlationId(supportCase.getCorrelationId())
                                .notes(supportCase.getNotes().stream()
                                                .map(this::toCaseNoteDto)
                                                .collect(Collectors.toList()))
                                .relatedTransactionIds(supportCase.getRelatedTransactions().stream()
                                                .map(Transaction::getId)
                                                .collect(Collectors.toList()))
                                .resolvedAt(supportCase.getResolvedAt())
                                .resolution(supportCase.getResolution())
                                .build();
        }

        private CaseNoteDto toCaseNoteDto(CaseNote note) {
                return CaseNoteDto.builder()
                                .id(note.getId())
                                .author(note.getAuthor())
                                .content(note.getContent())
                                .createdAt(note.getCreatedAt())
                                .build();
        }

        @Transactional
        public SupportCaseDto assignCase(Long caseId, AssignCaseRequest request) {
                SupportCase supportCase = caseRepository.findById(caseId)
                                .orElseThrow(() -> new IllegalArgumentException("Case not found with id: " + caseId));

                String oldAssignee = supportCase.getAssignedTo();
                supportCase.setAssignedTo(request.getAssignedTo());
                supportCase = caseRepository.save(supportCase);

                // Record audit event
                java.util.Map<String, Object> auditOldValue = new java.util.HashMap<>();
                auditOldValue.put("assignedTo", oldAssignee);
                java.util.Map<String, Object> auditNewValue = new java.util.HashMap<>();
                auditNewValue.put("assignedTo", request.getAssignedTo());
                auditEventService.recordEvent(
                                AuditEvent.EntityType.CASE,
                                caseId,
                                AuditEvent.Action.UPDATE,
                                auditOldValue,
                                auditNewValue,
                                request.getAssignedTo());

                return toDto(supportCase);
        }

        @Transactional
        public CaseNoteDto addCaseNote(Long caseId, AddCaseNoteRequest request, String author) {
                SupportCase supportCase = caseRepository.findById(caseId)
                                .orElseThrow(() -> new IllegalArgumentException("Case not found with id: " + caseId));

                CaseNote note = CaseNote.builder()
                                .supportCase(supportCase)
                                .author(author)
                                .content(request.getContent())
                                .build();

                note = caseNoteRepository.save(note);
                supportCase.getNotes().add(note);

                // Record audit event
                java.util.Map<String, Object> auditNewValue = new java.util.HashMap<>();
                auditNewValue.put("noteId", note.getId());
                auditNewValue.put("author", author);
                auditNewValue.put("content", request.getContent());
                auditEventService.recordEvent(
                                AuditEvent.EntityType.CASE,
                                caseId,
                                AuditEvent.Action.UPDATE,
                                null,
                                auditNewValue,
                                author);

                return toCaseNoteDto(note);
        }

        @Transactional
        public SupportCaseDto linkTransaction(Long caseId, Long transactionId) {
                SupportCase supportCase = caseRepository.findById(caseId)
                                .orElseThrow(() -> new IllegalArgumentException("Case not found with id: " + caseId));

                Transaction transaction = transactionRepository.findById(transactionId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Transaction not found with id: " + transactionId));

                if (!supportCase.getRelatedTransactions().contains(transaction)) {
                        supportCase.getRelatedTransactions().add(transaction);
                        supportCase = caseRepository.save(supportCase);

                        // Record audit event
                        java.util.Map<String, Object> auditNewValue = new java.util.HashMap<>();
                        auditNewValue.put("linkedTransactionId", transactionId);
                        auditEventService.recordEvent(
                                        AuditEvent.EntityType.CASE,
                                        caseId,
                                        AuditEvent.Action.UPDATE,
                                        null,
                                        auditNewValue,
                                        "SYSTEM");
                }

                return toDto(supportCase);
        }

        @Transactional
        public SupportCaseDto resolveCase(Long caseId, ResolveCaseRequest request) {
                SupportCase supportCase = caseRepository.findById(caseId)
                                .orElseThrow(() -> new IllegalArgumentException("Case not found with id: " + caseId));

                if (supportCase.getState() == CaseState.RESOLVED || supportCase.getState() == CaseState.CLOSED) {
                        throw new IllegalStateException("Case is already resolved or closed");
                }

                // Set resolution before transition (required for validation)
                supportCase.setResolution(request.getResolution());

                // Use state machine to transition to RESOLVED
                CaseState newState = stateMachine.executeTransitionToState(
                                supportCase, CaseState.RESOLVED, "SYSTEM",
                                supportCase.getCorrelationId(), "Case resolved: " + request.getResolution());

                supportCase.setState(newState);
                supportCase.setResolvedAt(java.time.LocalDateTime.now());
                supportCase = caseRepository.save(supportCase);

                return toDto(supportCase);
        }

        public com.bankops.portal.dto.CaseTransitionsDto getAllowedTransitions(Long caseId) {
                SupportCase supportCase = caseRepository.findById(caseId)
                                .orElseThrow(() -> new IllegalArgumentException("Case not found with id: " + caseId));

                CaseState currentState = supportCase.getState();
                List<com.bankops.portal.statemachine.CaseTransition> allowedTransitions = stateMachine
                                .getAllowedTransitions(currentState, supportCase);

                List<com.bankops.portal.dto.TransitionDto> transitionDtos = allowedTransitions.stream()
                                .map(transition -> com.bankops.portal.dto.TransitionDto.builder()
                                                .name(transition.name())
                                                .label(transition.getLabel())
                                                .targetState(transition.getToState().name())
                                                .requiresAssignee(transition.isRequiresAssignee())
                                                .requiresResolution(transition.isRequiresResolution())
                                                .build())
                                .collect(Collectors.toList());

                return com.bankops.portal.dto.CaseTransitionsDto.builder()
                                .caseId(caseId)
                                .currentState(currentState.name())
                                .allowedTransitions(transitionDtos)
                                .build();
        }
}
