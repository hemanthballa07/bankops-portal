package com.bankops.portal.service;

import com.bankops.portal.entity.LogEvent;
import com.bankops.portal.entity.Transaction;
import com.bankops.portal.repository.LogEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoggingService {
    
    private final LogEventRepository logEventRepository;
    private final ObjectMapper objectMapper;
    
    // Constructor injection - ObjectMapper is auto-configured by Spring Boot with JavaTimeModule
    public LoggingService(LogEventRepository logEventRepository, ObjectMapper objectMapper) {
        this.logEventRepository = logEventRepository;
        this.objectMapper = objectMapper;
    }
    
    @Transactional
    public void logEvent(String correlationId, LogEvent.LogLevel level, String message, Map<String, Object> context) {
        // Set correlation ID in MDC for structured logging
        MDC.put("correlationId", correlationId);
        
        // Log to application logs
        switch (level) {
            case DEBUG -> log.debug(message, context);
            case INFO -> log.info(message, context);
            case WARN -> log.warn(message, context);
            case ERROR -> log.error(message, context);
        }
        
        // Persist to database
        try {
            String contextJson = context != null ? objectMapper.writeValueAsString(context) : null;
            LogEvent logEvent = LogEvent.builder()
                    .correlationId(correlationId)
                    .level(level)
                    .message(message)
                    .contextJson(contextJson)
                    .build();
            
            logEventRepository.save(logEvent);
        } catch (Exception e) {
            log.error("Failed to persist log event", e);
        } finally {
            MDC.remove("correlationId");
        }
    }
    
    public void logTransactionEvent(Transaction transaction, LogEvent.LogLevel level, String message, Map<String, Object> context) {
        if (context == null) {
            context = new HashMap<>();
        }
        context.put("transactionId", transaction.getId());
        context.put("accountId", transaction.getAccount().getId());
        context.put("type", transaction.getType().name());
        context.put("amount", transaction.getAmount());
        
        logEvent(transaction.getCorrelationId(), level, message, context);
    }
}

