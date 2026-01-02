package com.bankops.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a single allowed transition
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransitionDto {
    private String name;
    private String label;
    private String targetState;
    private boolean requiresAssignee;
    private boolean requiresResolution;
}
