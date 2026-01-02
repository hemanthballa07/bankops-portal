package com.bankops.portal.repository;

import com.bankops.portal.entity.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, String> {

    /**
     * Find audit events for a specific entity, ordered by timestamp descending
     * (newest first).
     * Uses composite index (entity_type, entity_id, timestamp) for efficient
     * querying.
     */
    Page<AuditEvent> findByEntityTypeAndEntityIdOrderByTimestampDesc(
            AuditEvent.EntityType entityType,
            Long entityId,
            Pageable pageable);
}
