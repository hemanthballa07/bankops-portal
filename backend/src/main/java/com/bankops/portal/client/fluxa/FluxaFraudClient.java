package com.bankops.portal.client.fluxa;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.bankops.portal.config.FluxaProperties;
import com.bankops.portal.entity.LogEvent;
import com.bankops.portal.service.LoggingService;
import com.fluxa.fraud.v1.Decision;
import com.fluxa.fraud.v1.EvaluateRequest;
import com.fluxa.fraud.v1.EvaluateResponse;
import com.fluxa.fraud.v1.FraudEvalGrpc;
import com.google.protobuf.Timestamp;

import io.grpc.Deadline;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

/**
 * Synchronous gRPC caller for {@code fluxa.fraud.v1.FraudEval/EvaluateTransaction}.
 * Maps protocol-level outcomes to the {@link FluxaEvalOutcome} sealed hierarchy
 * so the calling service does not need to import gRPC or proto types.
 */
@Component
public class FluxaFraudClient {

    private final FraudEvalGrpc.FraudEvalBlockingStub stub;
    private final FluxaProperties props;
    private final LoggingService loggingService;

    public FluxaFraudClient(FraudEvalGrpc.FraudEvalBlockingStub stub,
            FluxaProperties props, LoggingService loggingService) {
        this.stub = stub;
        this.props = props;
        this.loggingService = loggingService;
    }

    public FluxaEvalOutcome evaluate(String correlationId, Long accountId, BigDecimal amount,
            String currency, String merchant, Instant txTime) {
        if (!props.enabled()) {
            return new FluxaEvalOutcome.Disabled();
        }

        Timestamp ts = Timestamp.newBuilder()
                .setSeconds(txTime.getEpochSecond())
                .setNanos(txTime.getNano())
                .build();
        EvaluateRequest req = EvaluateRequest.newBuilder()
                .setEventId(correlationId)
                .setUserId(String.valueOf(accountId))
                .setAmount(amount.doubleValue())
                .setCurrency(currency)
                .setMerchant(merchant)
                .setTransactionTime(ts)
                .build();

        long startNanos = System.nanoTime();
        try {
            EvaluateResponse resp = stub
                    .withDeadline(Deadline.after(props.deadline().toMillis(), TimeUnit.MILLISECONDS))
                    .evaluateTransaction(req);
            FluxaEvalOutcome outcome = mapResponse(resp);
            logOutcome(correlationId, outcome, resp, startNanos);
            return outcome;
        } catch (StatusRuntimeException e) {
            FluxaEvalOutcome outcome = mapError(e);
            logError(correlationId, outcome, e, startNanos);
            return outcome;
        }
    }

    private FluxaEvalOutcome mapResponse(EvaluateResponse resp) {
        if (resp.getDecision() == Decision.DECISION_FLAG) {
            List<FraudFlagDto> flags = resp.getFlagsList().stream()
                    .map(p -> new FraudFlagDto(p.getRuleName(), p.getRuleValue()))
                    .collect(Collectors.toList());
            return new FluxaEvalOutcome.Flag(flags, resp.getMlScore(), resp.getEvaluatedBy());
        }
        return new FluxaEvalOutcome.Allow();
    }

    private FluxaEvalOutcome mapError(StatusRuntimeException e) {
        Status.Code code = e.getStatus().getCode();
        return switch (code) {
            case DEADLINE_EXCEEDED -> new FluxaEvalOutcome.Timeout();
            case UNAVAILABLE -> new FluxaEvalOutcome.Unavailable();
            case INVALID_ARGUMENT -> new FluxaEvalOutcome.InvalidArgument(
                    e.getStatus().getDescription() != null ? e.getStatus().getDescription() : "");
            default -> new FluxaEvalOutcome.Unavailable();
        };
    }

    private void logOutcome(String correlationId, FluxaEvalOutcome outcome,
            EvaluateResponse resp, long startNanos) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("decision", resp.getDecision().name());
        ctx.put("latency_ms", (System.nanoTime() - startNanos) / 1_000_000.0);
        ctx.put("evaluated_by", resp.getEvaluatedBy());
        ctx.put("ml_score", resp.getMlScore());
        if (outcome instanceof FluxaEvalOutcome.Flag flag) {
            ctx.put("rule_names", flag.flags().stream().map(FraudFlagDto::ruleName).toList());
        }
        loggingService.logEvent(correlationId, LogEvent.LogLevel.INFO, "fluxa.eval", ctx);
    }

    private void logError(String correlationId, FluxaEvalOutcome outcome,
            StatusRuntimeException e, long startNanos) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("outcome", outcome.getClass().getSimpleName());
        ctx.put("grpc_code", e.getStatus().getCode().name());
        ctx.put("latency_ms", (System.nanoTime() - startNanos) / 1_000_000.0);
        if (e.getStatus().getDescription() != null) {
            ctx.put("description", e.getStatus().getDescription());
        }
        loggingService.logEvent(correlationId, LogEvent.LogLevel.WARN, "fluxa.eval_error", ctx);
    }
}
