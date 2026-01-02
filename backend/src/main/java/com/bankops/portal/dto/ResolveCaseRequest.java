package com.bankops.portal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResolveCaseRequest {

    @NotBlank(message = "Resolution is required")
    private String resolution;
}
