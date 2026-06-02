package com.bankops.portal.client.fluxguard;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bankops.portal.config.FluxguardProperties;
import com.fluxguard.grpc.ratelimit.v1.RateLimitGrpc;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import jakarta.annotation.PreDestroy;

/**
 * Wires the gRPC {@link ManagedChannel} and blocking-stub beans used by
 * {@link FluxguardRateLimitClient}. Activates {@link FluxguardProperties} binding.
 *
 * <p>When tracing is configured (an {@link OpenTelemetry} bean is present), the
 * channel carries an OpenTelemetry gRPC client interceptor so the W3C
 * {@code traceparent} is propagated to fluxguard and the bankops&rarr;fluxguard hop
 * joins the distributed trace. In the {@code test} profile tracing is disabled, so the
 * bean is absent and the channel is built without the interceptor.
 *
 * <p>Beans are created unconditionally so {@code TransactionService} can inject
 * {@link FluxguardRateLimitClient} without an {@code Optional}. The client itself
 * short-circuits on {@code !props.enabled()} so the channel is opened lazily
 * (Netty does not connect until the first RPC).
 *
 * <p>Bean names are kept distinct from the fluxa channel/stub beans so both clients
 * coexist in the same context.
 */
@Configuration
@EnableConfigurationProperties(FluxguardProperties.class)
public class FluxguardClientConfiguration {

    private ManagedChannel managedChannel;

    @Bean
    public ManagedChannel fluxguardManagedChannel(FluxguardProperties props,
            ObjectProvider<OpenTelemetry> openTelemetry) {
        NettyChannelBuilder builder = NettyChannelBuilder
                .forAddress(props.host(), props.port())
                .usePlaintext();
        OpenTelemetry otel = openTelemetry.getIfAvailable();
        if (otel != null) {
            builder.intercept(GrpcTelemetry.create(otel).newClientInterceptor());
        }
        this.managedChannel = builder.build();
        return this.managedChannel;
    }

    @Bean
    public RateLimitGrpc.RateLimitBlockingStub fluxguardStub(ManagedChannel fluxguardManagedChannel) {
        return RateLimitGrpc.newBlockingStub(fluxguardManagedChannel);
    }

    @PreDestroy
    public void shutdown() {
        if (managedChannel != null && !managedChannel.isShutdown()) {
            managedChannel.shutdown();
        }
    }
}
