package com.bankops.portal.repository;

import com.bankops.portal.entity.SupportCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportCaseRepository extends JpaRepository<SupportCase, Long> {
    
    @Query("SELECT c FROM SupportCase c WHERE " +
           "(:status IS NULL OR c.status = :status) AND " +
           "(:severity IS NULL OR c.severity = :severity)")
    List<SupportCase> findByStatusAndSeverity(
        @Param("status") SupportCase.CaseStatus status,
        @Param("severity") SupportCase.CaseSeverity severity
    );
    
    List<SupportCase> findByTransactionId(Long transactionId);
}





