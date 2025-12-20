package com.bankops.portal.controller;

import com.bankops.portal.dto.AccountDto;
import com.bankops.portal.dto.UpdateAccountRequest;
import com.bankops.portal.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountsController {
    
    private final AccountService accountService;
    
    @GetMapping("/{id}")
    public ResponseEntity<AccountDto> getAccountById(@PathVariable Long id) {
        AccountDto account = accountService.getAccountById(id);
        return ResponseEntity.ok(account);
    }
    
    @PatchMapping("/{id}")
    public ResponseEntity<AccountDto> updateAccount(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAccountRequest request) {
        AccountDto account = accountService.updateAccount(id, request);
        return ResponseEntity.ok(account);
    }
}

