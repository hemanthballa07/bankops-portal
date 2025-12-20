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
public class AccountDto {
    private Long id;
    private Long customerId;
    private String type;
    private String status;
    private BigDecimal balance;
    private Boolean overdraftEnabled;
    private LocalDateTime createdAt;
}

