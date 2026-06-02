package com.bankops.portal.client.fluxguard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.bankops.portal.config.FluxguardProperties;
import com.bankops.portal.service.LoggingService;
import com.fluxguard.grpc.ratelimit.v1.CheckLimitRequest;
import com.fluxguard.grpc.ratelimit.v1.CheckLimitResponse;
import com.fluxguard.grpc.ratelimit.v1.Decision;
import com.fluxguard.grpc.ratelimit.v1.RateLimitGrpc;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;

/**
 * Unit test for {@link FluxguardRateLimitClient}. Drives the client against an
 * in-process gRPC server whose {@link MutableRateLimitService} response is settable
 * per-test, mirroring the Fluxa client tests' wiring.
 */
class FluxguardRateLimitClientTest {

    private static final String SERVER = "fluxguard-client-test";

    private Server server;
    private ManagedChannel channel;
    private MutableRateLimitService service;
    private LoggingService loggingService;
    private FluxguardProperties enabledProps;
    private FluxguardProperties disabledProps;

    @BeforeEach
    void setUp() throws Exception {
        service = new MutableRateLimitService();
        server = InProcessServerBuilder.forName(SERVER).directExecutor()
                .addService(service).build().start();
        channel = InProcessChannelBuilder.forName(SERVER).directExecutor()
                .usePlaintext().build();
        loggingService = Mockito.mock(LoggingService.class);
        enabledProps = new FluxguardProperties(true, "localhost", 9099, Duration.ofMillis(150));
        disabledProps = new FluxguardProperties(false, "localhost", 9099, Duration.ofMillis(150));
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    private FluxguardRateLimitClient client(FluxguardProperties props) {
        return new FluxguardRateLimitClient(
                RateLimitGrpc.newBlockingStub(channel), props, loggingService);
    }

    @Test
    void allowResponse_mapsToAllow() {
        service.respondAllow();
        FluxguardRateLimitOutcome outcome =
                client(enabledProps).checkLimit("req-1", "alice", "idem-1");
        assertThat(outcome).isInstanceOf(FluxguardRateLimitOutcome.Allow.class);
    }

    @Test
    void denyResponse_mapsToDeniedWithRetryAfter() {
        service.respondDeny(2500L);
        FluxguardRateLimitOutcome outcome =
                client(enabledProps).checkLimit("req-2", "alice", "idem-2");
        assertThat(outcome).isInstanceOf(FluxguardRateLimitOutcome.Denied.class);
        assertThat(((FluxguardRateLimitOutcome.Denied) outcome).retryAfterMs()).isEqualTo(2500L);
    }

    @Test
    void serverError_mapsToUnavailable_failOpen() {
        service.respondError(Status.UNAVAILABLE);
        FluxguardRateLimitOutcome outcome =
                client(enabledProps).checkLimit("req-3", "alice", "idem-3");
        assertThat(outcome).isInstanceOf(FluxguardRateLimitOutcome.Unavailable.class);
    }

    @Test
    void disabledFlag_shortCircuitsToDisabled_withoutTouchingChannel() {
        service.respondAllow();
        FluxguardRateLimitOutcome outcome =
                client(disabledProps).checkLimit("req-4", "alice", "idem-4");
        assertThat(outcome).isInstanceOf(FluxguardRateLimitOutcome.Disabled.class);
        // No RPC was made — the server never saw a call.
        assertThat(service.callCount()).isZero();
    }

    /**
     * In-process gRPC service whose response is settable per-test, counting calls so
     * the disabled-flag test can assert no RPC was made.
     */
    static class MutableRateLimitService extends RateLimitGrpc.RateLimitImplBase {

        private CheckLimitResponse response;
        private Throwable error;
        private int callCount;

        void respondAllow() {
            this.error = null;
            this.response = CheckLimitResponse.newBuilder()
                    .setDecision(Decision.DECISION_ALLOW)
                    .setPolicyApplied("transaction")
                    .build();
        }

        void respondDeny(long retryAfterMs) {
            this.error = null;
            this.response = CheckLimitResponse.newBuilder()
                    .setDecision(Decision.DECISION_DENY)
                    .setRetryAfterMs(retryAfterMs)
                    .setPolicyApplied("transaction")
                    .build();
        }

        void respondError(Status status) {
            this.response = null;
            this.error = status.asRuntimeException();
        }

        int callCount() {
            return callCount;
        }

        @Override
        public void checkLimit(CheckLimitRequest request,
                StreamObserver<CheckLimitResponse> responseObserver) {
            callCount++;
            if (error != null) {
                responseObserver.onError(error);
                return;
            }
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}
