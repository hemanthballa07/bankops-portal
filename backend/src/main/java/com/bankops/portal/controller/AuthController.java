package com.bankops.portal.controller;

import lombok.Builder;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/whoami")
public class AuthController {

    @GetMapping
    public ResponseEntity<AuthStatus> whoami(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return ResponseEntity.ok(AuthStatus.builder()
                .authenticated(true)
                .username(authentication.getName())
                .roles(roles)
                .build());
    }

    @Data
    @Builder
    public static class AuthStatus {
        private boolean authenticated;
        private String username;
        private List<String> roles;
    }
}
