package com.bankops.portal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionFilterRequest {

    private LocalDate startDate;
    private LocalDate endDate;
    private String type; // DEPOSIT, WITHDRAWAL, or null for all
    private String status; // PENDING, COMPLETED, FAILED, or null for all
    private String searchText; // Searches description and correlationId

    @Min(0)
    @Builder.Default
    private int page = 0;

    @Min(1)
    @Max(100)
    @Builder.Default
    private int size = 20;
}
