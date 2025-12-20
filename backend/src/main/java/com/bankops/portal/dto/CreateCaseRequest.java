package com.bankops.portal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateCaseRequest {
    
    @NotNull(message = "Customer ID is required")
    private Long customerId;
    
    private Long accountId;
    
    private Long transactionId;
    
    @NotBlank(message = "Summary is required")
    private String summary;
    
    private String severity; // LOW, MEDIUM, HIGH, CRITICAL
}

