package com.bankops.portal.controller;

import com.bankops.portal.entity.Transaction;
import com.bankops.portal.dto.CreateTransactionRequest;
import com.bankops.portal.dto.MonthlySpendingDto;
import com.bankops.portal.dto.PagedResponse;
import com.bankops.portal.dto.ReviewTransactionRequest;
import com.bankops.portal.dto.SpendingSummaryDto;
import com.bankops.portal.dto.TransactionDto;
import com.bankops.portal.dto.TransactionFilterRequest;
import com.bankops.portal.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/accounts/{accountId}/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<TransactionDto> createTransaction(
            @PathVariable Long accountId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateTransactionRequest request) {
        TransactionDto transaction = transactionService.createTransaction(accountId, request, idempotencyKey);
        // HELD transactions are flagged by Fluxa and sent to ops review — surface as 202 ACCEPTED.
        HttpStatus status = Transaction.TransactionStatus.HELD.name().equals(transaction.getStatus())
                ? HttpStatus.ACCEPTED
                : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(transaction);
    }

    @GetMapping
    public ResponseEntity<PagedResponse<TransactionDto>> getTransactionsByAccountId(
            @PathVariable Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String searchText,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        TransactionFilterRequest filters = TransactionFilterRequest.builder()
                .startDate(startDate)
                .endDate(endDate)
                .type(type)
                .status(status)
                .searchText(searchText)
                .page(page)
                .size(Math.min(size, 100)) // Cap at 100
                .build();

        PagedResponse<TransactionDto> response = transactionService.getFilteredTransactions(accountId, filters);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionDto> getTransaction(
            @PathVariable Long accountId,
            @PathVariable Long transactionId) {
        return ResponseEntity.ok(transactionService.getTransaction(accountId, transactionId));
    }

    // SUPPORT/ADMIN only. `isAnonymous()` keeps the local-profile trifecta-console bypass
    // working (it reaches here unauthenticated); in test/prod a USER is authenticated and denied.
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('SUPPORT','ADMIN') or isAnonymous()")
    @PostMapping("/{transactionId}/release")
    public ResponseEntity<TransactionDto> releaseTransaction(
            @PathVariable Long accountId,
            @PathVariable Long transactionId,
            @RequestBody(required = false) ReviewTransactionRequest request) {
        String actorId = request != null ? request.getActorId() : null;
        String notes = request != null ? request.getNotes() : null;
        return ResponseEntity.ok(transactionService.releaseTransaction(accountId, transactionId, actorId, notes));
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('SUPPORT','ADMIN') or isAnonymous()")
    @PostMapping("/{transactionId}/reject")
    public ResponseEntity<TransactionDto> rejectTransaction(
            @PathVariable Long accountId,
            @PathVariable Long transactionId,
            @RequestBody(required = false) ReviewTransactionRequest request) {
        String actorId = request != null ? request.getActorId() : null;
        String notes = request != null ? request.getNotes() : null;
        return ResponseEntity.ok(transactionService.rejectTransaction(accountId, transactionId, actorId, notes));
    }

    @GetMapping("/spending-summary")
    public ResponseEntity<List<SpendingSummaryDto>> getSpendingSummary(
            @PathVariable Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        List<SpendingSummaryDto> summary = transactionService.getSpendingSummary(accountId, startDate, endDate);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/monthly-spending")
    public ResponseEntity<List<MonthlySpendingDto>> getMonthlySpending(
            @PathVariable Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        List<MonthlySpendingDto> monthlyData = transactionService.getMonthlySpending(accountId, startDate, endDate);
        return ResponseEntity.ok(monthlyData);
    }
}



