package com.bankops.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionDto {
    private Long id;
    private Long accountId;
    private String type;
    private BigDecimal amount;
    private String status;
    private String correlationId;
    private String description;
    private String category;
    private LocalDateTime createdAt;
}



