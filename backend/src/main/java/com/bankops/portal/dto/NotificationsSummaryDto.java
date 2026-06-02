package com.bankops.portal.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The full notifications payload: items + counts (counts reflect the true total before any cap). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationsSummaryDto {
    private List<NotificationDto> items;
    private Counts counts;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Counts {
        private long critical;
        private long warning;
        private long info;
        private long total;
    }
}
