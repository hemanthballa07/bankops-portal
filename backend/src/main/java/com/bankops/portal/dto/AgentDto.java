package com.bankops.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for Agent information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentDto {
    private Long id;
    private String name;
    private String email;
    private Boolean active;
    private Integer maxActiveCases;
    private Integer currentActiveCount;
    private List<String> skills;
}
