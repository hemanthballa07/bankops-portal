package com.bankops.portal.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One derived ops notification. Not persisted — built on read by NotificationsService. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDto {
    private String id;                  // stable synthetic id, e.g. HELD-42, CASE-7, BACKLOG
    private NotificationCategory category;
    private NotificationSeverity severity;
    private String title;
    private String detail;
    private String entityType;          // TRANSACTION | CASE
    private Long entityId;              // nullable (BACKLOG has none)
    private String link;                // frontend route, e.g. /fraud-review, /cases
    private LocalDateTime timestamp;    // nullable (BACKLOG has none)
}
