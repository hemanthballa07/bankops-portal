package com.bankops.portal.controller;

import com.bankops.portal.dto.AccountDto;
import com.bankops.portal.dto.CreateAccountRequest;
import com.bankops.portal.dto.UpdateAccountRequest;
import com.bankops.portal.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/customers/{customerId}/accounts")
@RequiredArgsConstructor
public class AccountController {
    
    private final AccountService accountService;
    
    @PostMapping
    public ResponseEntity<AccountDto> createAccount(
            @PathVariable Long customerId,
            @Valid @RequestBody CreateAccountRequest request) {
        AccountDto account = accountService.createAccount(customerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }
    
    @GetMapping
    public ResponseEntity<List<AccountDto>> getAccountsByCustomerId(@PathVariable Long customerId) {
        List<AccountDto> accounts = accountService.getAccountsByCustomerId(customerId);
        return ResponseEntity.ok(accounts);
    }
}

