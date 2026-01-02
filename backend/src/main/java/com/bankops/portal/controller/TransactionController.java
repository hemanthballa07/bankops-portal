package com.bankops.portal.controller;

import com.bankops.portal.dto.CreateTransactionRequest;
import com.bankops.portal.dto.MonthlySpendingDto;
import com.bankops.portal.dto.PagedResponse;
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
        return ResponseEntity.status(HttpStatus.CREATED).body(transaction);
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



