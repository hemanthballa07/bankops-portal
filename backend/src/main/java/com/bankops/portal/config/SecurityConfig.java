package com.bankops.portal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    
    private final CorsConfigurationSource corsConfigurationSource;
    
    public SecurityConfig(CorsConfigurationSource corsConfigurationSource) {
        this.corsConfigurationSource = corsConfigurationSource;
    }
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/health", "/actuator/health").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/api/customers/**").hasAnyRole("USER", "SUPPORT")
                .requestMatchers("/api/accounts/**").hasAnyRole("USER", "SUPPORT")
                .requestMatchers("/api/accounts/**/transactions/**").hasAnyRole("USER", "SUPPORT")
                .requestMatchers("/api/cases/**").hasRole("SUPPORT")
                .requestMatchers("/api/incidents/**").hasRole("SUPPORT")
                .anyRequest().authenticated()
            )
            .httpBasic(httpBasic -> {})
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.disable()) // For H2 console
            );
        
        return http.build();
    }
    
    @Bean
    public UserDetailsService userDetailsService() {
        // In production, these should come from environment variables or database
        String userUsername = System.getenv().getOrDefault("APP_USER_USERNAME", "user");
        String userPassword = System.getenv().getOrDefault("APP_USER_PASSWORD", "password");
        String supportUsername = System.getenv().getOrDefault("APP_SUPPORT_USERNAME", "support");
        String supportPassword = System.getenv().getOrDefault("APP_SUPPORT_PASSWORD", "password");
        
        UserDetails user = User.builder()
                .username(userUsername)
                .password(passwordEncoder().encode(userPassword))
                .roles("USER")
                .build();
        
        UserDetails support = User.builder()
                .username(supportUsername)
                .password(passwordEncoder().encode(supportPassword))
                .roles("USER", "SUPPORT")
                .build();
        
        return new InMemoryUserDetailsManager(user, support);
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

