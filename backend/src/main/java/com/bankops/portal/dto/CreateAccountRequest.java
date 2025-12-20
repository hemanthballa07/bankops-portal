package com.bankops.portal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAccountRequest {
    
    @NotBlank(message = "Account type is required")
    private String type; // CHEQUING or SAVINGS
}

