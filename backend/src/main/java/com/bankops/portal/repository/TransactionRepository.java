package com.bankops.portal.repository;

import com.bankops.portal.dto.MonthlySpendingDto;
import com.bankops.portal.dto.SpendingSummaryDto;
import com.bankops.portal.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

        List<Transaction> findByAccountIdOrderByCreatedAtDesc(Long accountId);

        List<Transaction> findAllByStatusOrderByCreatedAtDesc(Transaction.TransactionStatus status);

        Optional<Transaction> findByCorrelationId(String correlationId);

        Optional<Transaction> findByAccount_IdAndIdempotencyKeyAndType(
                        Long accountId, String idempotencyKey, Transaction.TransactionType type);

        @Query("SELECT t FROM Transaction t WHERE t.account.id = :accountId " +
                        "AND (:startDate IS NULL OR t.createdAt >= :startDate) " +
                        "AND (:endDate IS NULL OR t.createdAt < :endDate) " +
                        "AND (:type IS NULL OR t.type = :type) " +
                        "AND (:status IS NULL OR t.status = :status) " +
                        "AND (:searchText IS NULL OR " +
                        "     LOWER(t.description) LIKE LOWER(CONCAT('%', :searchText, '%')) OR " +
                        "     LOWER(t.correlationId) LIKE LOWER(CONCAT('%', :searchText, '%'))) " +
                        "ORDER BY t.createdAt DESC")
        Page<Transaction> findByAccountIdWithFilters(
                        @Param("accountId") Long accountId,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate,
                        @Param("type") Transaction.TransactionType type,
                        @Param("status") Transaction.TransactionStatus status,
                        @Param("searchText") String searchText,
                        Pageable pageable);

        @Query("SELECT new com.bankops.portal.dto.SpendingSummaryDto(" +
                        "CAST(t.category AS string), SUM(t.amount), COUNT(t)) " +
                        "FROM Transaction t " +
                        "WHERE t.account.id = :accountId " +
                        "AND t.type = 'WITHDRAWAL' " +
                        "AND (:startDate IS NULL OR t.createdAt >= :startDate) " +
                        "AND (:endDate IS NULL OR t.createdAt < :endDate) " +
                        "GROUP BY t.category " +
                        "ORDER BY SUM(t.amount) DESC")
        List<SpendingSummaryDto> getSpendingSummary(
                        @Param("accountId") Long accountId,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        @Query("SELECT new com.bankops.portal.dto.MonthlySpendingDto(" +
                        "CONCAT(CAST(YEAR(t.createdAt) AS string), '-', " +
                        "CASE WHEN MONTH(t.createdAt) < 10 THEN CONCAT('0', CAST(MONTH(t.createdAt) AS string)) " +
                        "ELSE CAST(MONTH(t.createdAt) AS string) END), " +
                        "SUM(t.amount)) " +
                        "FROM Transaction t " +
                        "WHERE t.account.id = :accountId " +
                        "AND t.type = 'WITHDRAWAL' " +
                        "AND (:startDate IS NULL OR t.createdAt >= :startDate) " +
                        "AND (:endDate IS NULL OR t.createdAt < :endDate) " +
                        "GROUP BY YEAR(t.createdAt), MONTH(t.createdAt) " +
                        "ORDER BY YEAR(t.createdAt), MONTH(t.createdAt)")
        List<MonthlySpendingDto> getMonthlySpending(
                        @Param("accountId") Long accountId,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        @org.springframework.data.jpa.repository.Query(
            "SELECT t.status, COUNT(t) FROM Transaction t GROUP BY t.status")
        java.util.List<Object[]> countGroupedByStatus();
}
