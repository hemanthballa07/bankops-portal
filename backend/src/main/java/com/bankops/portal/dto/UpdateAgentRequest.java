package com.bankops.portal.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;

@Data
public class UpdateAgentRequest {
    @NotBlank(message = "name is required")
    private String name;

    @Min(value = 1, message = "maxActiveCases must be at least 1")
    private Integer maxActiveCases = 10;

    private List<String> skills;

    private Boolean active = true;
}
