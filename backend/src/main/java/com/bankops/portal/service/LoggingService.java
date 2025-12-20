package com.bankops.portal.service;

import com.bankops.portal.entity.LogEvent;
import com.bankops.portal.entity.Transaction;
import com.bankops.portal.repository.LogEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class LoggingService {

    private final LogEventRepository logEventRepository;
    private final ObjectMapper objectMapper;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    public LoggingService(LogEventRepository logEventRepository,
            ObjectMapper objectMapper,
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.logEventRepository = logEventRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        this.transactionTemplate
                .setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Logs an event. Persistence is attempted in a separate transaction to avoid
     * rolling back the main business transaction if logging fails.
     */
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

        try {
            transactionTemplate.execute(status -> {
                try {
                    String contextJson = context != null ? objectMapper.writeValueAsString(context) : null;
                    LogEvent logEvent = LogEvent.builder()
                            .correlationId(correlationId)
                            .level(level)
                            .message(message)
                            .contextJson(contextJson)
                            .build();

                    logEventRepository.save(logEvent);
                    return null;
                } catch (Exception e) {
                    throw new RuntimeException("Error persisting log event", e);
                }
            });
        } catch (Exception e) {
            // Log warning but do not throw, ensuring business transaction continues
            log.warn("Failed to persist log event (Database error). Application log was successful. Error: {}",
                    e.getMessage());
        } finally {
            MDC.remove("correlationId");
        }
    }

    public void logTransactionEvent(Transaction transaction, LogEvent.LogLevel level, String message,
            Map<String, Object> context) {
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
