package com.bankops.portal.repository;

import com.bankops.portal.entity.SlaStatusChangeEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SlaStatusChangeEventRepository extends JpaRepository<SlaStatusChangeEvent, Long> {

    /**
     * Find all SLA status changes for a specific case, ordered by timestamp
     * descending
     */
    List<SlaStatusChangeEvent> findByCaseIdOrderByTimestampDesc(Long caseId);

    /**
     * Find all SLA status changes with a specific correlation ID
     */
    List<SlaStatusChangeEvent> findByCorrelationId(String correlationId);
}
