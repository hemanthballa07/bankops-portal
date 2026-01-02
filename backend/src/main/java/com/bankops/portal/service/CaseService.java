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

    @Transactional
    public SupportCaseDto createCase(CreateCaseRequest request) {
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(
                        () -> new IllegalArgumentException("Customer not found with id: " + request.getCustomerId()));

        Account account = null;
        if (request.getAccountId() != null) {
            account = accountRepository.findById(request.getAccountId())
                    .orElseThrow(
                            () -> new IllegalArgumentException("Account not found with id: " + request.getAccountId()));
        }

        Transaction transaction = null;
        if (request.getTransactionId() != null) {
            transaction = transactionRepository.findById(request.getTransactionId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Transaction not found with id: " + request.getTransactionId()));
        }

        SupportCase.CaseSeverity severity = SupportCase.CaseSeverity.MEDIUM;
        if (request.getSeverity() != null) {
            try {
                severity = SupportCase.CaseSeverity.valueOf(request.getSeverity().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid case severity: " + request.getSeverity());
            }
        }

        SupportCase supportCase = SupportCase.builder()
                .customer(customer)
                .account(account)
                .transaction(transaction)
                .status(SupportCase.CaseStatus.OPEN)
                .severity(severity)
                .summary(request.getSummary())
                .build();

        supportCase = caseRepository.save(supportCase);
        return toDto(supportCase);
    }

    public List<SupportCaseDto> getCases(SupportCase.CaseStatus status, SupportCase.CaseSeverity severity) {
        List<SupportCase> cases = caseRepository.findByStatusAndSeverity(status, severity);
        return cases.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public SupportCaseDto updateCaseStatus(Long id, UpdateCaseRequest request) {
        SupportCase supportCase = caseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Case not found with id: " + id));

        SupportCase.CaseStatus oldStatus = supportCase.getStatus();

        if (request.getStatus() != null) {
            SupportCase.CaseStatus newStatus;
            try {
                newStatus = SupportCase.CaseStatus.valueOf(request.getStatus().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid case status: " + request.getStatus());
            }

            // Validate status transition
            validateStatusTransition(supportCase.getStatus(), newStatus);
            supportCase.setStatus(newStatus);
        }

        supportCase = caseRepository.save(supportCase);

        // Record audit event if status changed
        if (request.getStatus() != null && !oldStatus.equals(supportCase.getStatus())) {
            java.util.Map<String, Object> auditOldValue = new java.util.HashMap<>();
            auditOldValue.put("status", oldStatus.name());
            java.util.Map<String, Object> auditNewValue = new java.util.HashMap<>();
            auditNewValue.put("status", supportCase.getStatus().name());
            auditEventService.recordEvent(
                    com.bankops.portal.entity.AuditEvent.EntityType.CASE,
                    id,
                    com.bankops.portal.entity.AuditEvent.Action.STATUS_CHANGE,
                    auditOldValue,
                    auditNewValue,
                    "SYSTEM");
        }

        return toDto(supportCase);
    }

    private void validateStatusTransition(SupportCase.CaseStatus currentStatus, SupportCase.CaseStatus newStatus) {
        // Valid transitions: OPEN -> INVESTIGATING -> RESOLVED
        // Also allow: OPEN -> RESOLVED (quick resolution)
        if (currentStatus == newStatus) {
            return; // No change
        }

        if (currentStatus == SupportCase.CaseStatus.OPEN) {
            if (newStatus == SupportCase.CaseStatus.INVESTIGATING || newStatus == SupportCase.CaseStatus.RESOLVED) {
                return; // Valid
            }
        } else if (currentStatus == SupportCase.CaseStatus.INVESTIGATING) {
            if (newStatus == SupportCase.CaseStatus.RESOLVED) {
                return; // Valid
            }
        } else if (currentStatus == SupportCase.CaseStatus.RESOLVED) {
            throw new IllegalStateException("Cannot change status from RESOLVED");
        }

        throw new IllegalStateException("Invalid status transition from " + currentStatus + " to " + newStatus);
    }

    private SupportCaseDto toDto(SupportCase supportCase) {
        return SupportCaseDto.builder()
                .id(supportCase.getId())
                .customerId(supportCase.getCustomer().getId())
                .accountId(supportCase.getAccount() != null ? supportCase.getAccount().getId() : null)
                .transactionId(supportCase.getTransaction() != null ? supportCase.getTransaction().getId() : null)
                .status(supportCase.getStatus().name())
                .severity(supportCase.getSeverity().name())
                .summary(supportCase.getSummary())
                .createdAt(supportCase.getCreatedAt())
                .updatedAt(supportCase.getUpdatedAt())
                .assignedTo(supportCase.getAssignedTo())
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
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found with id: " + transactionId));

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

        if (supportCase.getStatus() == SupportCase.CaseStatus.RESOLVED) {
            throw new IllegalStateException("Case is already resolved");
        }

        supportCase.setStatus(SupportCase.CaseStatus.RESOLVED);
        supportCase.setResolvedAt(java.time.LocalDateTime.now());
        supportCase.setResolution(request.getResolution());
        supportCase = caseRepository.save(supportCase);

        // Record audit event
        java.util.Map<String, Object> auditNewValue = new java.util.HashMap<>();
        auditNewValue.put("status", "RESOLVED");
        auditNewValue.put("resolution", request.getResolution());
        auditEventService.recordEvent(
                AuditEvent.EntityType.CASE,
                caseId,
                AuditEvent.Action.STATUS_CHANGE,
                null,
                auditNewValue,
                "SYSTEM");

        return toDto(supportCase);
    }
}
