package com.bankops.portal.client.fluxguard;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.bankops.portal.config.FluxguardProperties;
import com.bankops.portal.entity.LogEvent;
import com.bankops.portal.service.LoggingService;
import com.fluxguard.grpc.ratelimit.v1.CheckLimitRequest;
import com.fluxguard.grpc.ratelimit.v1.CheckLimitResponse;
import com.fluxguard.grpc.ratelimit.v1.Decision;
import com.fluxguard.grpc.ratelimit.v1.Policy;
import com.fluxguard.grpc.ratelimit.v1.RateLimitGrpc;
import com.fluxguard.grpc.ratelimit.v1.ReportLoginFailureRequest;

import io.grpc.StatusRuntimeException;

/**
 * Synchronous gRPC caller for {@code fluxguard.ratelimit.v1.RateLimit/CheckLimit}.
 * Maps protocol-level outcomes to the {@link FluxguardRateLimitOutcome} sealed hierarchy
 * so the calling service does not need to import gRPC or proto types.
 *
 * <p>Rate limiting is <b>fail-open</b>: any transport failure or timeout returns
 * {@link FluxguardRateLimitOutcome.Unavailable} so a fluxguard outage never blocks a
 * transaction on its own.
 */
@Component
public class FluxguardRateLimitClient {

    private final RateLimitGrpc.RateLimitBlockingStub stub;
    private final FluxguardProperties props;
    private final LoggingService loggingService;

    public FluxguardRateLimitClient(RateLimitGrpc.RateLimitBlockingStub stub,
            FluxguardProperties props, LoggingService loggingService) {
        this.stub = stub;
        this.props = props;
        this.loggingService = loggingService;
    }

    public FluxguardRateLimitOutcome checkLimit(String requestId, String subject, String idempotencyKey) {
        if (!props.enabled()) {
            return new FluxguardRateLimitOutcome.Disabled();
        }

        CheckLimitRequest req = CheckLimitRequest.newBuilder()
                .setRequestId(requestId)
                .setPolicy(Policy.POLICY_TRANSACTION)
                .setSubject(subject)
                .setIdempotencyKey(idempotencyKey)
                .build();

        long startNanos = System.nanoTime();
        try {
            CheckLimitResponse resp = stub
                    .withDeadlineAfter(props.deadline().toMillis(), MILLISECONDS)
                    .checkLimit(req);
            FluxguardRateLimitOutcome outcome = mapResponse(resp);
            logOutcome(requestId, outcome, resp, startNanos);
            return outcome;
        } catch (StatusRuntimeException e) {
            logError(requestId, e, startNanos);
            return new FluxguardRateLimitOutcome.Unavailable();
        }
    }

    public FluxguardRateLimitOutcome checkLogin(String requestId, String clientIp) {
        if (!props.enabled()) {
            return new FluxguardRateLimitOutcome.Disabled();
        }

        CheckLimitRequest req = CheckLimitRequest.newBuilder()
                .setRequestId(requestId)
                .setPolicy(Policy.POLICY_LOGIN)
                .setClientIp(clientIp)
                .build();

        long startNanos = System.nanoTime();
        try {
            CheckLimitResponse resp = stub
                    .withDeadlineAfter(props.deadline().toMillis(), MILLISECONDS)
                    .checkLimit(req);
            FluxguardRateLimitOutcome outcome = mapResponse(resp);
            logOutcome(requestId, outcome, resp, startNanos);
            return outcome;
        } catch (StatusRuntimeException e) {
            logError(requestId, e, startNanos);
            return new FluxguardRateLimitOutcome.Unavailable();
        }
    }

    public void reportLoginFailure(String requestId, String clientIp) {
        if (!props.enabled()) {
            return;
        }

        ReportLoginFailureRequest req = ReportLoginFailureRequest.newBuilder()
                .setRequestId(requestId)
                .setClientIp(clientIp)
                .build();

        try {
            stub.withDeadlineAfter(props.deadline().toMillis(), MILLISECONDS)
                    .reportLoginFailure(req);
        } catch (StatusRuntimeException e) {
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("grpc_code", e.getStatus().getCode().name());
            if (e.getStatus().getDescription() != null) {
                ctx.put("description", e.getStatus().getDescription());
            }
            loggingService.logEvent(requestId, LogEvent.LogLevel.WARN,
                    "fluxguard.report_login_failure_error", ctx);
        }
    }

    private FluxguardRateLimitOutcome mapResponse(CheckLimitResponse resp) {
        if (resp.getDecision() == Decision.DECISION_DENY) {
            return new FluxguardRateLimitOutcome.Denied(resp.getRetryAfterMs());
        }
        return new FluxguardRateLimitOutcome.Allow();
    }

    private void logOutcome(String requestId, FluxguardRateLimitOutcome outcome,
            CheckLimitResponse resp, long startNanos) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("decision", resp.getDecision().name());
        ctx.put("latency_ms", (System.nanoTime() - startNanos) / 1_000_000.0);
        ctx.put("policy_applied", resp.getPolicyApplied());
        if (outcome instanceof FluxguardRateLimitOutcome.Denied denied) {
            ctx.put("retry_after_ms", denied.retryAfterMs());
        }
        loggingService.logEvent(requestId, LogEvent.LogLevel.INFO, "fluxguard.check", ctx);
    }

    private void logError(String requestId, StatusRuntimeException e, long startNanos) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("outcome", "Unavailable");
        ctx.put("grpc_code", e.getStatus().getCode().name());
        ctx.put("latency_ms", (System.nanoTime() - startNanos) / 1_000_000.0);
        if (e.getStatus().getDescription() != null) {
            ctx.put("description", e.getStatus().getDescription());
        }
        loggingService.logEvent(requestId, LogEvent.LogLevel.WARN, "fluxguard.check_error", ctx);
    }
}
