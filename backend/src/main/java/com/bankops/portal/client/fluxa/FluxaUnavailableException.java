package com.bankops.portal.client.fluxa;

/**
 * Thrown by {@code TransactionService} when the Fluxa fraud-eval RPC is
 * unavailable (or times out) AND the configured failure policy for the current
 * direction (deposit vs withdrawal) is {@code FAIL_CLOSED}. Mapped to HTTP 503
 * by a {@code @ControllerAdvice} {@code @ExceptionHandler}.
 */
public class FluxaUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FluxaUnavailableException() {
        super("Fluxa fraud-eval is unavailable; request rejected under FAIL_CLOSED policy");
    }
}
