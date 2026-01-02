package com.bankops.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlySpendingDto {
    private String month; // Format: YYYY-MM
    private BigDecimal totalAmount;
}
