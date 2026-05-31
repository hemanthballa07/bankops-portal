package com.bankops.portal.client.fluxa;

import java.util.List;

/**
 * Outcome of a single {@link FluxaFraudClient#evaluate} call. Sealed so the
 * dispatch in {@code TransactionService} covers every variant explicitly.
 */
public sealed interface FluxaEvalOutcome
        permits FluxaEvalOutcome.Allow,
                FluxaEvalOutcome.Flag,
                FluxaEvalOutcome.Unavailable,
                FluxaEvalOutcome.Timeout,
                FluxaEvalOutcome.InvalidArgument,
                FluxaEvalOutcome.Disabled {

    /** Fluxa returned DECISION_ALLOW — caller proceeds normally. */
    record Allow() implements FluxaEvalOutcome {
    }

    /** Fluxa returned DECISION_FLAG — caller transitions tx to HELD. */
    record Flag(List<FraudFlagDto> flags) implements FluxaEvalOutcome {
    }

    /** Fluxa returned Status.UNAVAILABLE or a non-deadline transport error. */
    record Unavailable() implements FluxaEvalOutcome {
    }

    /** Fluxa returned Status.DEADLINE_EXCEEDED. */
    record Timeout() implements FluxaEvalOutcome {
    }

    /** Fluxa returned Status.INVALID_ARGUMENT — caller bug; surface as HTTP 400. */
    record InvalidArgument(String message) implements FluxaEvalOutcome {
    }

    /** Feature flag {@code bankops.fluxa.enabled} is false — no RPC was made. */
    record Disabled() implements FluxaEvalOutcome {
    }
}
