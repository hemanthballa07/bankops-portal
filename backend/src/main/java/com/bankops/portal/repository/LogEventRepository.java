package com.bankops.portal.repository;

import com.bankops.portal.entity.LogEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LogEventRepository extends JpaRepository<LogEvent, Long> {
    
    List<LogEvent> findByCorrelationIdOrderByCreatedAtAsc(String correlationId);
}





