package com.bankops.portal.controller;

import com.bankops.portal.entity.LogEvent;
import com.bankops.portal.repository.LogEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/log-events")
@RequiredArgsConstructor
public class LogEventController {

    private final LogEventRepository logEventRepository;
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE);

    @GetMapping("/by-correlation/{correlationId}")
    public ResponseEntity<List<LogEvent>> getLogsByCorrelationId(@PathVariable String correlationId) {
        // Validation
        if (correlationId == null || !UUID_PATTERN.matcher(correlationId).matches()) {
            throw new IllegalArgumentException("Invalid correlation ID format. Expected UUID.");
        }

        List<LogEvent> logs = logEventRepository.findByCorrelationIdOrderByCreatedAtAsc(correlationId);
        return ResponseEntity.ok(logs);
    }
}
