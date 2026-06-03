package com.bankops.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportCaseDto {
    private Long id;
    private Long customerId;
    private Long accountId;
    private Long transactionId;
    private Double mlScore;       // advisory ML risk of the linked txn (null if none)
    private String evaluatedBy;   // Fluxa eval/model version that scored the linked txn
    private String status;
    private String severity;
    private String summary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String assignedTo;
    private String correlationId;
    private List<CaseNoteDto> notes;
    private List<Long> relatedTransactionIds;
    private LocalDateTime resolvedAt;
    private String resolution;

    // SLA fields
    private String priority;
    private LocalDateTime slaDueAt;
    private String slaStatus;
    private Long slaRemainingSeconds;
}
