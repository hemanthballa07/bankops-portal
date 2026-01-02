package com.bankops.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO representing the current state and allowed transitions for a case
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaseTransitionsDto {
    private Long caseId;
    private String currentState;
    private List<TransitionDto> allowedTransitions;
}
