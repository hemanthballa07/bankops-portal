package com.bankops.portal.client.fluxa;

/**
 * Domain-friendly representation of a single fraud-rule fire. Mapped from
 * the gRPC-generated {@code com.fluxa.fraud.v1.FraudFlag} inside
 * {@link FluxaFraudClient} so proto types do not leak into the service layer.
 */
public record FraudFlagDto(String ruleName, String ruleValue) {
}
