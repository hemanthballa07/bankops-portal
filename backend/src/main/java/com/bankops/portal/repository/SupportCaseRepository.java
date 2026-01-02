package com.bankops.portal.repository;

import com.bankops.portal.entity.SupportCase;
import com.bankops.portal.statemachine.CaseState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportCaseRepository extends JpaRepository<SupportCase, Long> {

        @Query("SELECT c FROM SupportCase c WHERE " +
                        "(:state IS NULL OR c.state = :state) AND " +
                        "(:severity IS NULL OR c.severity = :severity)")
        List<SupportCase> findByStateAndSeverity(
                        @Param("state") CaseState state,
                        @Param("severity") SupportCase.CaseSeverity severity);

        List<SupportCase> findByTransactionId(Long transactionId);

        /**
         * Find all cases in any of the specified states (for SLA scheduler)
         */
        List<SupportCase> findByStateIn(List<CaseState> states);
}
