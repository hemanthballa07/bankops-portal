package com.bankops.portal.client.fluxguard;

/**
 * Outcome of a single {@link FluxguardRateLimitClient#checkLimit} call. Sealed so the
 * dispatch in {@code TransactionService} covers every variant explicitly.
 */
public sealed interface FluxguardRateLimitOutcome
        permits FluxguardRateLimitOutcome.Allow,
                FluxguardRateLimitOutcome.Denied,
                FluxguardRateLimitOutcome.Unavailable,
                FluxguardRateLimitOutcome.Disabled {

    /** fluxguard returned DECISION_ALLOW — caller proceeds normally. */
    record Allow() implements FluxguardRateLimitOutcome {
    }

    /** fluxguard returned DECISION_DENY — caller fails fast with HTTP 429. */
    record Denied(long retryAfterMs) implements FluxguardRateLimitOutcome {
    }

    /** fluxguard RPC failed (transport error / timeout) — fail-open, caller proceeds. */
    record Unavailable() implements FluxguardRateLimitOutcome {
    }

    /** Feature flag {@code bankops.fluxguard.enabled} is false — no RPC was made. */
    record Disabled() implements FluxguardRateLimitOutcome {
    }
}
