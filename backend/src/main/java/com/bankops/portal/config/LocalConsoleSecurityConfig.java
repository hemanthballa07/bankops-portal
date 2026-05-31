package com.bankops.portal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Permits trifecta-console REST calls without credentials in the local profile.
 * Evaluated before SecurityConfig (Order 1 vs default Order 100) so these paths
 * bypass the main HTTP Basic filter chain entirely.
 */
@Configuration
@Profile("local")
public class LocalConsoleSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain localConsoleFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(
                "/transactions/**",
                "/cases/**",
                "/accounts/*/transactions/*/release",
                "/accounts/*/transactions/*/reject"
            )
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
