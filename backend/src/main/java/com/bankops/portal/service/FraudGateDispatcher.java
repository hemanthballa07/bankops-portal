package com.bankops.portal.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.bankops.portal.client.fluxa.FluxaEvalOutcome;
import com.bankops.portal.client.fluxa.FluxaUnavailableException;
import com.bankops.portal.client.fluxa.FraudFlagDto;
import com.bankops.portal.config.FluxaProperties;
import com.bankops.portal.entity.AuditEvent;
import com.bankops.portal.entity.LogEvent;
import com.bankops.portal.entity.Transaction;
import com.bankops.portal.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Acts on a precomputed {@link FluxaEvalOutcome} for the transaction hot path. On FLAG it persists a
 * HELD transaction + auto-creates a P1 case and returns the HELD entity; it returns {@code null} when
 * the caller should fall through to the success path (Allow / Disabled / shadow / fail-open); and it
 * throws to surface caller-bugs (InvalidArgument → {@link IllegalArgumentException} → 400) or
 * FAIL_CLOSED policy hits (Unavailable/Timeout → {@link FluxaUnavailableException} → 503).
 *
 * <p>Extracted verbatim from {@code TransactionService.dispatchFluxaOutcome} (the only behavioural
 * change is returning the {@link Transaction} entity instead of a DTO, so the caller owns mapping).
 */
@Component
@RequiredArgsConstructor
public class FraudGateDispatcher {

    private final TransactionRepository transactionRepository;
    private final CaseService caseService;
    private final AuditEventService auditEventService;
    private final LoggingService loggingService;
    private final FluxaProperties fluxaProperties;

    /** Returns the HELD {@link Transaction} on FLAG, or {@code null} when the caller falls through. */
    public Transaction dispatch(Transaction.TransactionBuilder builder, FluxaEvalOutcome outcome,
            String correlationId, boolean isDeposit) {
        if (fluxaProperties.shadowMode()) {
            // Shadow mode is a pure observer — it NEVER throws, even on InvalidArgument (logged +
            // treated as Allow), to keep the observer posture.
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("outcome", outcome.getClass().getSimpleName());
            if (outcome instanceof FluxaEvalOutcome.Flag flag) {
                ctx.put("rule_names", flag.flags().stream().map(FraudFlagDto::ruleName).toList());
            } else if (outcome instanceof FluxaEvalOutcome.InvalidArgument ia) {
                ctx.put("description", ia.message());
            }
            loggingService.logEvent(correlationId, LogEvent.LogLevel.INFO, "fluxa.shadow", ctx);
            return null;
        }

        if (outcome instanceof FluxaEvalOutcome.Allow
                || outcome instanceof FluxaEvalOutcome.Disabled) {
            return null;
        }
        if (outcome instanceof FluxaEvalOutcome.Flag flag) {
            Transaction held = builder.status(Transaction.TransactionStatus.HELD)
                    .mlScore(flag.mlScore())
                    .evaluatedBy(flag.evaluatedBy())
                    .build();
            held = transactionRepository.save(held);
            caseService.createForFraud(held, flag.flags());

            Map<String, Object> newValue = new HashMap<>();
            newValue.put("status", "HELD");
            newValue.put("transactionId", held.getId());
            newValue.put("rules", flag.flags().stream().map(FraudFlagDto::ruleName).toList());
            auditEventService.recordEvent(
                    AuditEvent.EntityType.TRANSACTION,
                    held.getId(),
                    AuditEvent.Action.CREATE,
                    new HashMap<>(),
                    newValue,
                    correlationId);
            return held;
        }
        if (outcome instanceof FluxaEvalOutcome.InvalidArgument ia) {
            throw new IllegalArgumentException("Fluxa rejected: " + ia.message());
        }
        if (outcome instanceof FluxaEvalOutcome.Unavailable
                || outcome instanceof FluxaEvalOutcome.Timeout) {
            FluxaProperties.Failure policy = isDeposit
                    ? fluxaProperties.deposit()
                    : fluxaProperties.withdrawal();
            if (policy == FluxaProperties.Failure.FAIL_CLOSED) {
                throw new FluxaUnavailableException();
            }
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("outcome", outcome.getClass().getSimpleName());
            ctx.put("policy", policy.name());
            loggingService.logEvent(correlationId, LogEvent.LogLevel.WARN, "fluxa.fail_open", ctx);
            return null;
        }
        return null;
    }
}
