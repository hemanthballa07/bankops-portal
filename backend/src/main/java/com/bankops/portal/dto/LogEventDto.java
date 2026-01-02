package com.bankops.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogEventDto {
    private Long id;
    private String correlationId;
    private String level;
    private String message;
    private String contextJson;
    private LocalDateTime createdAt;
}





