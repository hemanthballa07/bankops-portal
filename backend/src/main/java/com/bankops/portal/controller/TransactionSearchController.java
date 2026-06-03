package com.bankops.portal.controller;

import com.bankops.portal.dto.TransactionDto;
import com.bankops.portal.entity.Transaction;
import com.bankops.portal.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionSearchController {

    private final TransactionService transactionService;
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE);

    @GetMapping
    public ResponseEntity<List<TransactionDto>> search(@RequestParam(required = false) String status) {
        if (status != null && !status.isBlank()) {
            Transaction.TransactionStatus txStatus = Transaction.TransactionStatus.valueOf(status.toUpperCase());
            return ResponseEntity.ok(transactionService.getTransactionsByStatus(txStatus));
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/held")
    public ResponseEntity<com.bankops.portal.dto.PagedResponse<TransactionDto>> heldPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(transactionService.getHeldTransactionsPaged(page, size, sort));
    }

    @GetMapping("/by-correlation/{correlationId}")
    public ResponseEntity<TransactionDto> getTransactionByCorrelationId(@PathVariable String correlationId) {
        // Validation
        if (correlationId == null || !UUID_PATTERN.matcher(correlationId).matches()) {
            throw new IllegalArgumentException("Invalid correlation ID format. Expected UUID.");
        }

        TransactionDto transaction = transactionService.getTransactionByCorrelationId(correlationId);
        return ResponseEntity.ok(transaction);
    }
}
