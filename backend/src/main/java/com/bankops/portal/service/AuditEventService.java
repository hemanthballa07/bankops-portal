package com.bankops.portal.service;

import com.bankops.portal.entity.AuditEvent;
import com.bankops.portal.repository.AuditEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditEventService {

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Records an audit event within the current transaction.
     * If this method fails, the entire transaction will rollback.
     * 
     * @param entityType  Type of entity (ACCOUNT, CASE, TRANSACTION)
     * @param entityId    ID of the entity
     * @param action      Action performed (CREATE, UPDATE, STATUS_CHANGE,
     *                    FLAG_TOGGLE)
     * @param oldValue    Previous state (will be serialized to JSON)
     * @param newValue    New state (will be serialized to JSON)
     * @param performedBy User/role/correlationId that performed the action
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void recordEvent(
            AuditEvent.EntityType entityType,
            Long entityId,
            AuditEvent.Action action,
            Object oldValue,
            Object newValue,
            String performedBy) {
        try {
            String oldValueJson = oldValue != null ? objectMapper.writeValueAsString(oldValue) : null;
            String newValueJson = newValue != null ? objectMapper.writeValueAsString(newValue) : null;

            AuditEvent event = AuditEvent.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .action(action)
                    .oldValue(oldValueJson)
                    .newValue(newValueJson)
                    .performedBy(performedBy)
                    .timestamp(LocalDateTime.now())
                    .build();

            auditEventRepository.save(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audit event values", e);
        }
    }

    /**
     * Retrieves paginated audit timeline for a specific entity.
     * 
     * @param entityType Type of entity
     * @param entityId   ID of the entity
     * @param page       Page number (0-indexed)
     * @param size       Page size
     * @return Page of audit events ordered by timestamp descending
     */
    public Page<AuditEvent> getAuditTimeline(
            AuditEvent.EntityType entityType,
            Long entityId,
            int page,
            int size) {
        Pageable pageable = PageRequest.of(page, size);
        return auditEventRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(
                entityType, entityId, pageable);
    }
}
