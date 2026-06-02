package com.bankops.portal.exception;

/**
 * Thrown by {@code TransactionService} when fluxguard returns DECISION_DENY for the
 * transaction policy — the request is over its rate limit. Mapped to HTTP 429
 * (Too Many Requests) with a {@code Retry-After} header by a {@code @ControllerAdvice}
 * {@code @ExceptionHandler}.
 */
public class FluxguardRateLimitException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final long retryAfterMs;

    public FluxguardRateLimitException(long retryAfterMs) {
        super("Rate limit exceeded; retry after " + retryAfterMs + "ms");
        this.retryAfterMs = retryAfterMs;
    }

    public long getRetryAfterMs() {
        return retryAfterMs;
    }
}
