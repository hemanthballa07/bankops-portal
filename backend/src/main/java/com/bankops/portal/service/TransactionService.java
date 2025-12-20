package com.bankops.portal.service;

import com.bankops.portal.dto.CreateTransactionRequest;
import com.bankops.portal.dto.TransactionDto;
import com.bankops.portal.entity.Account;
import com.bankops.portal.entity.LogEvent;
import com.bankops.portal.entity.Transaction;
import com.bankops.portal.repository.AccountRepository;
import com.bankops.portal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public TransactionDto createTransaction(Long accountId, CreateTransactionRequest request) {
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

        return toDto(transaction);
    }

    public List<TransactionDto> getTransactionsByAccountId(Long accountId) {
        List<Transaction> transactions = transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
        return transactions.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public TransactionDto getTransactionByCorrelationId(String correlationId) {
        return transactionRepository.findByCorrelationId(correlationId)
                .map(this::toDto)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transaction not found with correlationId: " + correlationId));
    }

    private TransactionDto toDto(Transaction transaction) {
        return TransactionDto.builder()
                .id(transaction.getId())
                .accountId(transaction.getAccount().getId())
                .type(transaction.getType().name())
                .amount(transaction.getAmount())
                .status(transaction.getStatus().name())
                .correlationId(transaction.getCorrelationId())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
