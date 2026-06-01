package com.bankops.portal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;

@Data
public class CreateAgentRequest {
    @NotBlank(message = "name is required")
    private String name;

    @NotBlank(message = "email is required")
    @Email(message = "email must be valid")
    private String email;

    @Min(value = 1, message = "maxActiveCases must be at least 1")
    private Integer maxActiveCases = 10;

    private List<String> skills;
}
