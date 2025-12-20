package com.bankops.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportCaseDto {
    private Long id;
    private Long customerId;
    private Long accountId;
    private Long transactionId;
    private String status;
    private String severity;
    private String summary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

