package com.bankops.portal.assignment;

import com.bankops.portal.entity.Agent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of an assignment operation (auto or manual).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentResult {
    private boolean success;
    private String reason;
    private Agent assignedAgent;
    private String policyVersion;
}
