package com.bankops.portal.client.fluxa;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bankops.portal.config.FluxaProperties;
import com.fluxa.fraud.v1.FraudEvalGrpc;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import jakarta.annotation.PreDestroy;

/**
 * Wires the gRPC {@link ManagedChannel} and blocking-stub beans used by
 * {@link FluxaFraudClient}. Activates {@link FluxaProperties} binding.
 *
 * <p>When tracing is configured (an {@link OpenTelemetry} bean is present), the
 * channel carries an OpenTelemetry gRPC client interceptor so the W3C
 * {@code traceparent} is propagated to Fluxa and the bankops&rarr;fluxa hop joins
 * the distributed trace. In the {@code test} profile tracing is disabled, so the
 * bean is absent and the channel is built without the interceptor.
 *
 * <p>Beans are created unconditionally so {@code TransactionService} can inject
 * {@link FluxaFraudClient} without an {@code Optional}. The client itself
 * short-circuits on {@code !props.enabled()} so the channel is opened lazily
 * (Netty does not connect until the first RPC).
 */
@Configuration
@EnableConfigurationProperties(FluxaProperties.class)
public class FluxaClientConfiguration {

    private ManagedChannel managedChannel;

    @Bean
    public ManagedChannel fluxaManagedChannel(FluxaProperties props,
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
    public FraudEvalGrpc.FraudEvalBlockingStub fluxaStub(ManagedChannel fluxaManagedChannel) {
        return FraudEvalGrpc.newBlockingStub(fluxaManagedChannel);
    }

    @PreDestroy
    public void shutdown() {
        if (managedChannel != null && !managedChannel.isShutdown()) {
            managedChannel.shutdown();
        }
    }
}
