package com.bankops.portal.service;

import com.bankops.portal.dto.CreateCustomerRequest;
import com.bankops.portal.dto.CustomerDto;
import com.bankops.portal.entity.Customer;
import com.bankops.portal.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService {
    
    private final CustomerRepository customerRepository;
    
    @Transactional
    public CustomerDto createCustomer(CreateCustomerRequest request) {
        // Check if email already exists
        customerRepository.findByEmail(request.getEmail())
                .ifPresent(c -> {
                    throw new IllegalArgumentException("Customer with email " + request.getEmail() + " already exists");
                });
        
        Customer customer = Customer.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .build();
        
        customer = customerRepository.save(customer);
        return toDto(customer);
    }
    
    public List<CustomerDto> searchCustomers(String query) {
        List<Customer> customers = query == null || query.trim().isEmpty()
                ? customerRepository.findAll()
                : customerRepository.searchCustomers(query.trim());
        
        return customers.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
    
    public CustomerDto getCustomerById(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with id: " + id));
        return toDto(customer);
    }
    
    private CustomerDto toDto(Customer customer) {
        return CustomerDto.builder()
                .id(customer.getId())
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .createdAt(customer.getCreatedAt())
                .build();
    }
}

