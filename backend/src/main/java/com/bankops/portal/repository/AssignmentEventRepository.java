package com.bankops.portal.repository;

import com.bankops.portal.entity.AssignmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssignmentEventRepository extends JpaRepository<AssignmentEvent, Long> {

    /**
     * Find all assignment events for a specific case, ordered by timestamp
     * descending
     */
    List<AssignmentEvent> findByCaseIdOrderByTimestampDesc(Long caseId);

    /**
     * Find all assignment events with a specific correlation ID
     */
    List<AssignmentEvent> findByCorrelationId(String correlationId);

    /**
     * Find all assignment events by actor
     */
    List<AssignmentEvent> findByActorOrderByTimestampDesc(String actor);
}
