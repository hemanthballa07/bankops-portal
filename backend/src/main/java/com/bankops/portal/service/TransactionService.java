package com.bankops.portal.service;

import com.bankops.portal.dto.CreateTransactionRequest;
import com.bankops.portal.dto.MonthlySpendingDto;
import com.bankops.portal.dto.PagedResponse;
import com.bankops.portal.dto.SpendingSummaryDto;
import com.bankops.portal.dto.TransactionDto;
import com.bankops.portal.dto.TransactionFilterRequest;
import com.bankops.portal.entity.Account;
import com.bankops.portal.entity.LogEvent;
import com.bankops.portal.entity.Transaction;
import com.bankops.portal.repository.AccountRepository;
import com.bankops.portal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final LoggingService loggingService;
    private final AuditEventService auditEventService;

    public TransactionDto createTransaction(Long accountId, CreateTransactionRequest request, String idempotencyKey) {
        // Route withdrawals to idempotent path
        Transaction.TransactionType transactionType;
        try {
            transactionType = Transaction.TransactionType.valueOf(request.getType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid transaction type: " + request.getType());
        }

        if (transactionType == Transaction.TransactionType.WITHDRAWAL) {
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("Idempotency-Key header is required for withdrawals");
            }
            return withdrawWithOptimisticRetry(accountId, request, idempotencyKey);
        }

        // Deposit path (existing logic)
        return createDeposit(accountId, request);
    }

    private TransactionDto withdrawWithOptimisticRetry(Long accountId, CreateTransactionRequest request,
            String idempotencyKey) {
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return withdrawOnce(accountId, request, idempotencyKey);
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException
                    | jakarta.persistence.OptimisticLockException e) {
                Map<String, Object> retryContext = new HashMap<>();
                retryContext.put("accountId", accountId);
                retryContext.put("idempotencyKey", idempotencyKey);
                retryContext.put("attempt", attempt);
                retryContext.put("maxRetries", maxRetries);

                loggingService.logEvent(idempotencyKey, LogEvent.LogLevel.WARN,
                        "withdraw.optimistic_retry", retryContext);

                if (attempt == maxRetries) {
                    throw new IllegalStateException(
                            "Withdrawal failed after " + maxRetries + " optimistic lock retries", e);
                }
            }
        }
        throw new IllegalStateException("Unreachable");
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    protected TransactionDto withdrawOnce(Long accountId, CreateTransactionRequest request, String idempotencyKey) {
        // 1) Idempotency pre-check
        var existing = transactionRepository.findByAccount_IdAndIdempotencyKeyAndType(
                accountId, idempotencyKey, Transaction.TransactionType.WITHDRAWAL);

        if (existing.isPresent()) {
            Map<String, Object> dupContext = new HashMap<>();
            dupContext.put("accountId", accountId);
            dupContext.put("idempotencyKey", idempotencyKey);
            dupContext.put("transactionId", existing.get().getId());
            loggingService.logEvent(idempotencyKey, LogEvent.LogLevel.INFO,
                    "withdraw.duplicate", dupContext);
            return toDto(existing.get());
        }

        // 2) Load account (managed entity with optimistic locking)
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found with id: " + accountId));

        if (account.getStatus() == Account.AccountStatus.CLOSED) {
            throw new IllegalStateException("Cannot process transaction on closed account");
        }

        BigDecimal amount = request.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }

        // 3) Funds check
        BigDecimal newBalance = account.getBalance().subtract(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0 && !Boolean.TRUE.equals(account.getOverdraftEnabled())) {
            Map<String, Object> insufficientContext = new HashMap<>();
            insufficientContext.put("accountId", accountId);
            insufficientContext.put("idempotencyKey", idempotencyKey);
            insufficientContext.put("balance", account.getBalance());
            insufficientContext.put("amount", amount);
            loggingService.logEvent(idempotencyKey, LogEvent.LogLevel.WARN,
                    "withdraw.insufficient_funds", insufficientContext);
            throw new IllegalStateException("Insufficient funds. Overdraft not enabled.");
        }

        // 4) Update balance (optimistic lock will protect concurrent updates)
        account.setBalance(newBalance);

        // 5) Parse category
        Transaction.TransactionCategory category = Transaction.TransactionCategory.OTHER;
        if (request.getCategory() != null && !request.getCategory().isEmpty()) {
            try {
                category = Transaction.TransactionCategory.valueOf(request.getCategory().toUpperCase());
            } catch (IllegalArgumentException e) {
                category = Transaction.TransactionCategory.OTHER;
            }
        }

        // 6) Create transaction with idempotency key
        String correlationId = UUID.randomUUID().toString();
        Transaction transaction = Transaction.builder()
                .account(account)
                .type(Transaction.TransactionType.WITHDRAWAL)
                .amount(amount)
                .status(Transaction.TransactionStatus.COMPLETED)
                .correlationId(correlationId)
                .description(request.getDescription())
                .category(category)
                .idempotencyKey(idempotencyKey)
                .build();

        try {
            transaction = transactionRepository.save(transaction);
            accountRepository.save(account);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Unique constraint triggered -> return existing txn
            var dup = transactionRepository.findByAccount_IdAndIdempotencyKeyAndType(
                    accountId, idempotencyKey, Transaction.TransactionType.WITHDRAWAL)
                    .orElseThrow(() -> e);

            Map<String, Object> raceContext = new HashMap<>();
            raceContext.put("accountId", accountId);
            raceContext.put("idempotencyKey", idempotencyKey);
            raceContext.put("transactionId", dup.getId());
            loggingService.logEvent(idempotencyKey, LogEvent.LogLevel.INFO,
                    "withdraw.duplicate_race", raceContext);
            return toDto(dup);
        }

        // 7) Log success
        Map<String, Object> successContext = new HashMap<>();
        successContext.put("accountId", accountId);
        successContext.put("idempotencyKey", idempotencyKey);
        successContext.put("transactionId", transaction.getId());
        successContext.put("amount", amount);
        successContext.put("newBalance", account.getBalance());
        loggingService.logEvent(correlationId, LogEvent.LogLevel.INFO,
                "withdraw.success", successContext);

        // 8) Record audit event
        Map<String, Object> auditOldValue = new HashMap<>();
        auditOldValue.put("balance", newBalance.add(amount));
        Map<String, Object> auditNewValue = new HashMap<>();
        auditNewValue.put("balance", newBalance);
        auditNewValue.put("transactionId", transaction.getId());
        auditEventService.recordEvent(
                com.bankops.portal.entity.AuditEvent.EntityType.ACCOUNT,
                accountId,
                com.bankops.portal.entity.AuditEvent.Action.UPDATE,
                auditOldValue,
                auditNewValue,
                correlationId);

        return toDto(transaction);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    private TransactionDto createDeposit(Long accountId, CreateTransactionRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found with id: " + accountId));

        if (account.getStatus() == Account.AccountStatus.CLOSED) {
            throw new IllegalStateException("Cannot process transaction on closed account");
        }

        Transaction.TransactionType transactionType;
        try {
            transactionType = Transaction.TransactionType.valueOf(request.getType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid transaction type: " + request.getType());
        }

        // Validate amount
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be greater than 0");
        }

        // Generate correlation ID
        String correlationId = UUID.randomUUID().toString();

        // Parse category
        Transaction.TransactionCategory category = Transaction.TransactionCategory.OTHER;
        if (request.getCategory() != null && !request.getCategory().isEmpty()) {
            try {
                category = Transaction.TransactionCategory.valueOf(request.getCategory().toUpperCase());
            } catch (IllegalArgumentException e) {
                // Invalid category, default to OTHER
                category = Transaction.TransactionCategory.OTHER;
            }
        }

        // Log transaction creation
        Map<String, Object> logContext = new HashMap<>();
        logContext.put("accountId", accountId);
        logContext.put("type", transactionType.name());
        logContext.put("amount", request.getAmount());
        loggingService.logEvent(correlationId, LogEvent.LogLevel.INFO, "Transaction request received", logContext);

        // Business rule: Check balance for withdrawals
        if (transactionType == Transaction.TransactionType.WITHDRAWAL) {
            BigDecimal newBalance = account.getBalance().subtract(request.getAmount());
            if (newBalance.compareTo(BigDecimal.ZERO) < 0 && !Boolean.TRUE.equals(account.getOverdraftEnabled())) {
                loggingService.logEvent(correlationId, LogEvent.LogLevel.WARN,
                        "Transaction failed: Insufficient funds", logContext);
                throw new IllegalStateException("Insufficient funds. Overdraft not enabled.");
            }
        }

        // Create transaction
        Transaction transaction = Transaction.builder()
                .account(account)
                .type(transactionType)
                .amount(request.getAmount())
                .status(Transaction.TransactionStatus.PENDING)
                .correlationId(correlationId)
                .description(request.getDescription())
                .category(category)
                .build();

        transaction = transactionRepository.save(transaction);

        // Update account balance
        if (transactionType == Transaction.TransactionType.DEPOSIT) {
            account.setBalance(account.getBalance().add(request.getAmount()));
        } else {
            account.setBalance(account.getBalance().subtract(request.getAmount()));
        }

        accountRepository.save(account);

        // Update transaction status
        transaction.setStatus(Transaction.TransactionStatus.COMPLETED);
        transaction = transactionRepository.save(transaction);

        // Log successful transaction
        logContext.put("transactionId", transaction.getId());
        logContext.put("newBalance", account.getBalance());
        loggingService.logTransactionEvent(transaction, LogEvent.LogLevel.INFO,
                "Transaction completed successfully", logContext);

        // Record audit event for deposit
        if (transactionType == Transaction.TransactionType.DEPOSIT) {
            Map<String, Object> auditOldValue = new HashMap<>();
            auditOldValue.put("balance", account.getBalance().subtract(request.getAmount()));
            Map<String, Object> auditNewValue = new HashMap<>();
            auditNewValue.put("balance", account.getBalance());
            auditNewValue.put("transactionId", transaction.getId());
            auditEventService.recordEvent(
                    com.bankops.portal.entity.AuditEvent.EntityType.ACCOUNT,
                    accountId,
                    com.bankops.portal.entity.AuditEvent.Action.UPDATE,
                    auditOldValue,
                    auditNewValue,
                    correlationId);
        }

        return toDto(transaction);
    }

    public List<TransactionDto> getTransactionsByAccountId(Long accountId) {
        List<Transaction> transactions = transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
        return transactions.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public PagedResponse<TransactionDto> getFilteredTransactions(Long accountId, TransactionFilterRequest filters) {
        // Convert LocalDate to LocalDateTime for query
        LocalDateTime startDateTime = filters.getStartDate() != null
                ? filters.getStartDate().atStartOfDay()
                : null;
        LocalDateTime endDateTime = filters.getEndDate() != null
                ? filters.getEndDate().atTime(LocalTime.MAX)
                : null;

        // Parse enum values
        Transaction.TransactionType type = null;
        if (filters.getType() != null && !filters.getType().isEmpty()) {
            try {
                type = Transaction.TransactionType.valueOf(filters.getType().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid transaction type: " + filters.getType());
            }
        }

        Transaction.TransactionStatus status = null;
        if (filters.getStatus() != null && !filters.getStatus().isEmpty()) {
            try {
                status = Transaction.TransactionStatus.valueOf(filters.getStatus().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid transaction status: " + filters.getStatus());
            }
        }

        // Create pageable
        Pageable pageable = PageRequest.of(filters.getPage(), filters.getSize());

        // Execute query
        Page<Transaction> page = transactionRepository.findByAccountIdWithFilters(
                accountId, startDateTime, endDateTime, type, status,
                filters.getSearchText(), pageable);

        // Build response
        return PagedResponse.<TransactionDto>builder()
                .content(page.getContent().stream()
                        .map(this::toDto)
                        .collect(Collectors.toList()))
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    public TransactionDto getTransactionByCorrelationId(String correlationId) {
        return transactionRepository.findByCorrelationId(correlationId)
                .map(this::toDto)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transaction not found with correlationId: " + correlationId));
    }

    public List<SpendingSummaryDto> getSpendingSummary(Long accountId, LocalDate startDate, LocalDate endDate) {
        // Convert LocalDate to LocalDateTime
        LocalDateTime startDateTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = endDate != null ? endDate.atTime(LocalTime.MAX) : null;

        return transactionRepository.getSpendingSummary(accountId, startDateTime, endDateTime);
    }

    public List<MonthlySpendingDto> getMonthlySpending(Long accountId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = endDate != null ? endDate.atTime(LocalTime.MAX) : null;

        return transactionRepository.getMonthlySpending(accountId, startDateTime, endDateTime);
    }

    private TransactionDto toDto(Transaction transaction) {
        return TransactionDto.builder()
                .id(transaction.getId())
                .accountId(transaction.getAccount().getId())
                .type(transaction.getType().name())
                .amount(transaction.getAmount())
                .status(transaction.getStatus().name())
                .correlationId(transaction.getCorrelationId())
                .description(transaction.getDescription())
                .category(transaction.getCategory().name())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
