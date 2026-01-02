package com.bankops.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentResponse {
    private String correlationId;
    private TransactionDto transaction;
    private SupportCaseDto case_;
    private List<LogEventDto> logEvents;
}





