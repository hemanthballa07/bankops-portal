package com.bankops.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for assignment operation results.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentResultDto {
    private boolean success;
    private String reason;
    private AgentDto assignedAgent;
    private String policyVersion;
}
