package com.bankops.portal.controller;

import com.bankops.portal.dto.CreateCustomerRequest;
import com.bankops.portal.dto.CustomerDto;
import com.bankops.portal.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {
    
    private final CustomerService customerService;
    
    @PostMapping
    public ResponseEntity<CustomerDto> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerDto customer = customerService.createCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(customer);
    }
    
    @GetMapping
    public ResponseEntity<List<CustomerDto>> searchCustomers(@RequestParam(required = false) String query) {
        List<CustomerDto> customers = customerService.searchCustomers(query);
        return ResponseEntity.ok(customers);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<CustomerDto> getCustomerById(@PathVariable Long id) {
        CustomerDto customer = customerService.getCustomerById(id);
        return ResponseEntity.ok(customer);
    }
}





