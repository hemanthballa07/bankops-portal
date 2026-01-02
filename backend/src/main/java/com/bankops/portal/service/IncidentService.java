package com.bankops.portal.service;

import com.bankops.portal.dto.IncidentResponse;
import com.bankops.portal.dto.LogEventDto;
import com.bankops.portal.dto.SupportCaseDto;
import com.bankops.portal.dto.TransactionDto;
import com.bankops.portal.entity.LogEvent;
import com.bankops.portal.entity.SupportCase;
import com.bankops.portal.entity.Transaction;
import com.bankops.portal.repository.LogEventRepository;
import com.bankops.portal.repository.SupportCaseRepository;
import com.bankops.portal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IncidentService {

    private final TransactionRepository transactionRepository;
    private final SupportCaseRepository caseRepository;
    private final LogEventRepository logEventRepository;

    public IncidentResponse getIncidentByCorrelationId(String correlationId) {
        // Find transaction
        Transaction transaction = transactionRepository.findByCorrelationId(correlationId)
                .orElse(null);

        TransactionDto transactionDto = null;
        if (transaction != null) {
            transactionDto = TransactionDto.builder()
                    .id(transaction.getId())
                    .accountId(transaction.getAccount().getId())
                    .type(transaction.getType().name())
                    .amount(transaction.getAmount())
                    .status(transaction.getStatus().name())
                    .correlationId(transaction.getCorrelationId())
                    .createdAt(transaction.getCreatedAt())
                    .build();
        }

        // Find related case
        SupportCaseDto caseDto = null;
        if (transaction != null) {
            List<SupportCase> cases = caseRepository.findByTransactionId(transaction.getId());
            if (!cases.isEmpty()) {
                SupportCase supportCase = cases.get(0); // Get first case
                caseDto = SupportCaseDto.builder()
                        .id(supportCase.getId())
                        .customerId(supportCase.getCustomer().getId())
                        .accountId(supportCase.getAccount() != null ? supportCase.getAccount().getId() : null)
                        .transactionId(
                                supportCase.getTransaction() != null ? supportCase.getTransaction().getId() : null)
                        .status(supportCase.getState().name())
                        .severity(supportCase.getSeverity().name())
                        .summary(supportCase.getSummary())
                        .createdAt(supportCase.getCreatedAt())
                        .updatedAt(supportCase.getUpdatedAt())
                        .build();
            }
        }

        // Find log events
        List<LogEvent> logEvents = logEventRepository.findByCorrelationIdOrderByCreatedAtAsc(correlationId);
        List<LogEventDto> logEventDtos = logEvents.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return IncidentResponse.builder()
                .correlationId(correlationId)
                .transaction(transactionDto)
                .case_(caseDto)
                .logEvents(logEventDtos)
                .build();
    }

    private LogEventDto toDto(LogEvent logEvent) {
        return LogEventDto.builder()
                .id(logEvent.getId())
                .correlationId(logEvent.getCorrelationId())
                .level(logEvent.getLevel().name())
                .message(logEvent.getMessage())
                .contextJson(logEvent.getContextJson())
                .createdAt(logEvent.getCreatedAt())
                .build();
    }
}
