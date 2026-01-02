package com.bankops.portal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateTransactionRequest {

    @NotBlank(message = "Transaction type is required")
    private String type; // DEPOSIT or WITHDRAWAL

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    private String description; // Optional transaction description

    private String category; // Optional category (defaults to OTHER if not provided)
}



