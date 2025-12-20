package com.bankops.portal.controller;

import com.bankops.portal.dto.CreateTransactionRequest;
import com.bankops.portal.dto.TransactionDto;
import com.bankops.portal.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/accounts/{accountId}/transactions")
@RequiredArgsConstructor
public class TransactionController {
    
    private final TransactionService transactionService;
    
    @PostMapping
    public ResponseEntity<TransactionDto> createTransaction(
            @PathVariable Long accountId,
            @Valid @RequestBody CreateTransactionRequest request) {
        TransactionDto transaction = transactionService.createTransaction(accountId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(transaction);
    }
    
    @GetMapping
    public ResponseEntity<List<TransactionDto>> getTransactionsByAccountId(@PathVariable Long accountId) {
        List<TransactionDto> transactions = transactionService.getTransactionsByAccountId(accountId);
        return ResponseEntity.ok(transactions);
    }
}

