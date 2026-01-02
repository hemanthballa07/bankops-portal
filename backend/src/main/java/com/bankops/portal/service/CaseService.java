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
import com.bankops.portal.sla.SlaService;
import com.bankops.portal.sla.SlaPriority;
import com.bankops.portal.statemachine.CaseState;
import com.bankops.portal.statemachine.CaseStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaseService {

        private final SupportCaseRepository caseRepository;
        private final CustomerRepository customerRepository;
        private final AccountRepository accountRepository;
        private final TransactionRepository transactionRepository;
        private final CaseNoteRepository caseNoteRepository;
        private final AuditEventService auditEventService;
        private final CaseStateMachine stateMachine;
        private final SlaService slaService;
        private final com.bankops.portal.assignment.AssignmentService assignmentService;

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

                // Initialize SLA with default P2 priority
                slaService.initializeSla(supportCase, SlaPriority.P2);
                supportCase = caseRepository.save(supportCase);

                // Attempt auto-assignment for new cases
                try {
                        com.bankops.portal.assignment.AssignmentResult assignmentResult = assignmentService
                                        .autoAssign(supportCase.getId(), correlationId);
                        if (assignmentResult.isSuccess()) {
                                log.info("Auto-assigned case {} to agent {} (correlation: {})",
                                                supportCase.getId(),
                                                assignmentResult.getAssignedAgent().getName(),
                                                correlationId);
                                // Reload case to get updated assignment
                                supportCase = caseRepository.findById(supportCase.getId())
                                                .orElse(supportCase);
                        } else {
                                log.info("Auto-assignment not performed for case {}: {} (correlation: {})",
                                                supportCase.getId(), assignmentResult.getReason(), correlationId);
                        }
                } catch (Exception e) {
                        log.warn("Auto-assignment failed for case {} (correlation: {}): {}",
                                        supportCase.getId(), correlationId, e.getMessage());
                        // Continue - assignment failure shouldn't block case creation
                }

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

                        // SLA lifecycle hooks based on state transitions
                        if (newState == CaseState.PENDING_CUSTOMER && oldState != CaseState.PENDING_CUSTOMER) {
                                // Entering PENDING_CUSTOMER: pause SLA
                                slaService.pauseSla(supportCase);
                        } else if (oldState == CaseState.PENDING_CUSTOMER && newState != CaseState.PENDING_CUSTOMER) {
                                // Leaving PENDING_CUSTOMER: resume SLA
                                slaService.resumeSla(supportCase);
                        }

                        if (newState == CaseState.RESOLVED || newState == CaseState.CLOSED) {
                                // Entering terminal state: stop SLA
                                slaService.stopSla(supportCase);
                        }
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
                                .priority(supportCase.getPriority() != null ? supportCase.getPriority().name() : null)
                                .slaDueAt(supportCase.getSlaDueAt())
                                .slaStatus(supportCase.getSlaStatus() != null ? supportCase.getSlaStatus().name()
                                                : null)
                                .slaRemainingSeconds(slaService.getRemainingTime(supportCase) != null
                                                ? slaService.getRemainingTime(supportCase).getSeconds()
                                                : null)
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

        /**
         * Get KPI metrics for case dashboard
         */
        public com.bankops.portal.dto.CaseKpiDto getKpis() {
                List<SupportCase> allCases = caseRepository.findAll();

                int open = (int) allCases.stream()
                                .filter(c -> c.getState() != CaseState.RESOLVED && c.getState() != CaseState.CLOSED)
                                .count();

                int unassigned = (int) allCases.stream()
                                .filter(c -> c.getAssignedTo() == null && c.getState() == CaseState.NEW)
                                .count();

                int slaAtRisk = (int) allCases.stream()
                                .filter(c -> c.getSlaStatus() == com.bankops.portal.sla.SlaStatus.AT_RISK
                                                || c.getSlaStatus() == com.bankops.portal.sla.SlaStatus.BREACHED)
                                .count();

                int highSeverity = (int) allCases.stream()
                                .filter(c -> c.getSeverity() == SupportCase.CaseSeverity.HIGH)
                                .count();

                int unassignedHighSeverity = (int) allCases.stream()
                                .filter(c -> c.getAssignee() == null)
                                .filter(c -> c.getSeverity() == SupportCase.CaseSeverity.HIGH)
                                .filter(c -> c.getState() != CaseState.RESOLVED && c.getState() != CaseState.CLOSED)
                                .count();

                return com.bankops.portal.dto.CaseKpiDto.builder()
                                .openCases(open)
                                .unassignedCases(unassigned)
                                .slaAtRiskCases(slaAtRisk)
                                .highSeverityCases(highSeverity)
                                .unassignedHighSeverity(unassignedHighSeverity)
                                .build();
        }
}
