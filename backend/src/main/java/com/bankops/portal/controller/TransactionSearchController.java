package com.bankops.portal.controller;

import com.bankops.portal.dto.TransactionDto;
import com.bankops.portal.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionSearchController {

    private final TransactionService transactionService;
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE);

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
