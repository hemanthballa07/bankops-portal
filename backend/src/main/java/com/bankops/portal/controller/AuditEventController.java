package com.bankops.portal.controller;

import com.bankops.portal.dto.AuditEventDto;
import com.bankops.portal.dto.PagedResponse;
import com.bankops.portal.entity.AuditEvent;
import com.bankops.portal.service.AuditEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditEventController {

    private final AuditEventService auditEventService;

    /**
     * Get audit timeline for a specific entity.
     * Protected by RBAC - only ADMIN and SUPPORT roles can access.
     * 
     * @param entityType Type of entity (ACCOUNT, CASE, TRANSACTION)
     * @param entityId   ID of the entity
     * @param page       Page number (default: 0)
     * @param size       Page size (default: 20)
     * @return Paginated list of audit events
     */
    @GetMapping("/{entityType}/{entityId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT')")
    public ResponseEntity<PagedResponse<AuditEventDto>> getAuditTimeline(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Validate and parse entity type
        AuditEvent.EntityType type;
        try {
            type = AuditEvent.EntityType.valueOf(entityType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid entity type: " + entityType +
                    ". Valid types are: ACCOUNT, CASE, TRANSACTION");
        }

        // Validate pagination parameters
        if (page < 0) {
            throw new IllegalArgumentException("Page number must be >= 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }

        // Fetch audit events
        Page<AuditEvent> auditPage = auditEventService.getAuditTimeline(type, entityId, page, size);

        // Build response
        PagedResponse<AuditEventDto> response = PagedResponse.<AuditEventDto>builder()
                .content(auditPage.getContent().stream()
                        .map(this::toDto)
                        .collect(Collectors.toList()))
                .page(auditPage.getNumber())
                .size(auditPage.getSize())
                .totalElements(auditPage.getTotalElements())
                .totalPages(auditPage.getTotalPages())
                .build();

        return ResponseEntity.ok(response);
    }

    private AuditEventDto toDto(AuditEvent event) {
        return AuditEventDto.builder()
                .id(event.getId())
                .entityType(event.getEntityType().name())
                .entityId(event.getEntityId())
                .action(event.getAction().name())
                .oldValue(event.getOldValue())
                .newValue(event.getNewValue())
                .performedBy(event.getPerformedBy())
                .timestamp(event.getTimestamp())
                .build();
    }
}
