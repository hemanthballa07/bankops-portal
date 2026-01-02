package com.bankops.portal.repository;

import com.bankops.portal.entity.StateTransitionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StateTransitionEventRepository extends JpaRepository<StateTransitionEvent, Long> {

    /**
     * Find all state transitions for a specific case, ordered by timestamp
     * descending
     */
    List<StateTransitionEvent> findByCaseIdOrderByTimestampDesc(Long caseId);

    /**
     * Find all state transitions with a specific correlation ID
     */
    List<StateTransitionEvent> findByCorrelationId(String correlationId);

    /**
     * Find all transitions performed by a specific actor
     */
    List<StateTransitionEvent> findByActorOrderByTimestampDesc(String actor);
}
