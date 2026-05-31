package com.bankops.portal.client.fluxa;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bankops.portal.config.FluxaProperties;
import com.fluxa.fraud.v1.FraudEvalGrpc;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import jakarta.annotation.PreDestroy;

/**
 * Wires the gRPC {@link ManagedChannel} and blocking-stub beans used by
 * {@link FluxaFraudClient}. Activates {@link FluxaProperties} binding.
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
    public ManagedChannel fluxaManagedChannel(FluxaProperties props) {
        this.managedChannel = NettyChannelBuilder
                .forAddress(props.host(), props.port())
                .usePlaintext()
                .build();
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
