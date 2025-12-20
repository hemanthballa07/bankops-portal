package com.bankops.portal.service;

import com.bankops.portal.dto.AccountDto;
import com.bankops.portal.dto.CreateAccountRequest;
import com.bankops.portal.dto.UpdateAccountRequest;
import com.bankops.portal.entity.Account;
import com.bankops.portal.entity.Customer;
import com.bankops.portal.repository.AccountRepository;
import com.bankops.portal.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {
    
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    
    @Transactional
    public AccountDto createAccount(Long customerId, CreateAccountRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with id: " + customerId));
        
        Account.AccountType accountType;
        try {
            accountType = Account.AccountType.valueOf(request.getType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid account type: " + request.getType());
        }
        
        Account account = Account.builder()
                .customer(customer)
                .type(accountType)
                .status(Account.AccountStatus.OPEN)
                .balance(java.math.BigDecimal.ZERO)
                .overdraftEnabled(false)
                .build();
        
        account = accountRepository.save(account);
        return toDto(account);
    }
    
    public List<AccountDto> getAccountsByCustomerId(Long customerId) {
        List<Account> accounts = accountRepository.findByCustomerId(customerId);
        return accounts.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
    
    public AccountDto getAccountById(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found with id: " + id));
        return toDto(account);
    }
    
    @Transactional
    public AccountDto updateAccount(Long id, UpdateAccountRequest request) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found with id: " + id));
        
        if (request.getStatus() != null) {
            try {
                account.setStatus(Account.AccountStatus.valueOf(request.getStatus().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid account status: " + request.getStatus());
            }
        }
        
        if (request.getOverdraftEnabled() != null) {
            account.setOverdraftEnabled(request.getOverdraftEnabled());
        }
        
        account = accountRepository.save(account);
        return toDto(account);
    }
    
    private AccountDto toDto(Account account) {
        return AccountDto.builder()
                .id(account.getId())
                .customerId(account.getCustomer().getId())
                .type(account.getType().name())
                .status(account.getStatus().name())
                .balance(account.getBalance())
                .overdraftEnabled(account.getOverdraftEnabled())
                .createdAt(account.getCreatedAt())
                .build();
    }
}

