package com.bankops.portal.timeline;

import com.bankops.portal.dto.CaseTimelineEventDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Filter criteria for timeline queries.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimelineFilter {
    private CaseTimelineEventDto.EventType eventType;
    private LocalDateTime fromTimestamp;
    private LocalDateTime toTimestamp;

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int pageSize = 50;
}
