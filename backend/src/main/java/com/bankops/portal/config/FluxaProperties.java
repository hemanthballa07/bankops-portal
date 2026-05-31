package com.bankops.portal.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the synchronous Fluxa fraud-eval gRPC client.
 *
 * <p>Bound from the {@code bankops.fluxa.*} block in application.yaml. Activated by
 * {@code @EnableConfigurationProperties(FluxaProperties.class)} on
 * {@code FluxaClientConfiguration}.
 */
@ConfigurationProperties(prefix = "bankops.fluxa")
public record FluxaProperties(
        boolean enabled,
        boolean shadowMode,
        String host,
        int port,
        Duration deadline,
        Failure withdrawal,
        Failure deposit) {

    public enum Failure {
        FAIL_OPEN,
        FAIL_CLOSED
    }
}
