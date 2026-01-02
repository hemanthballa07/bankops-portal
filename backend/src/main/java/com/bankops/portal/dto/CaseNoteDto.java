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
public class CaseNoteDto {
    private Long id;
    private String author;
    private String content;
    private LocalDateTime createdAt;
}
