package com.bankops.portal.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the synchronous fluxguard rate-limit gRPC client.
 *
 * <p>Bound from the {@code bankops.fluxguard.*} block in application.yaml. Activated by
 * {@code @EnableConfigurationProperties(FluxguardProperties.class)} on
 * {@code FluxguardClientConfiguration}.
 *
 * <p>Rate-limit fail-open is unconditional — a fluxguard outage must never block a
 * transaction — so there is no directional (deposit/withdrawal) failure policy here.
 */
@ConfigurationProperties(prefix = "bankops.fluxguard")
public record FluxguardProperties(
        boolean enabled,
        String host,
        int port,
        Duration deadline) {
}
