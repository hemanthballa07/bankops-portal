package com.bankops.portal.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "first_name", nullable = false)
    private String firstName;
    
    @Column(name = "last_name", nullable = false)
    private String lastName;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(nullable = false)
    private String phone;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Account> accounts = new ArrayList<>();
    
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SupportCase> cases = new ArrayList<>();
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}





