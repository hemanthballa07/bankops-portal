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
import com.fluxguard.grpc.ratelimit.v1.Policy;
import com.fluxguard.grpc.ratelimit.v1.RateLimitGrpc;
import com.fluxguard.grpc.ratelimit.v1.ReportLoginFailureRequest;
import com.fluxguard.grpc.ratelimit.v1.ReportLoginFailureResponse;

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

    @Test
    void checkLogin_allowResponse_mapsToAllow() {
        service.respondAllow();
        FluxguardRateLimitOutcome outcome =
                client(enabledProps).checkLogin("login-req-1", "203.0.113.7");
        assertThat(outcome).isInstanceOf(FluxguardRateLimitOutcome.Allow.class);
        assertThat(service.lastCheckRequest().getPolicy()).isEqualTo(Policy.POLICY_LOGIN);
        assertThat(service.lastCheckRequest().getClientIp()).isEqualTo("203.0.113.7");
        assertThat(service.lastCheckRequest().getSubject()).isEmpty();
    }

    @Test
    void checkLogin_denyResponse_mapsToDeniedWithRetryAfter() {
        service.respondDeny(2500L);
        FluxguardRateLimitOutcome outcome =
                client(enabledProps).checkLogin("login-req-2", "203.0.113.7");
        assertThat(outcome).isInstanceOf(FluxguardRateLimitOutcome.Denied.class);
        assertThat(((FluxguardRateLimitOutcome.Denied) outcome).retryAfterMs()).isEqualTo(2500L);
    }

    @Test
    void checkLogin_serverError_mapsToUnavailable_failOpen() {
        service.respondError(Status.UNAVAILABLE);
        FluxguardRateLimitOutcome outcome =
                client(enabledProps).checkLogin("login-req-3", "203.0.113.7");
        assertThat(outcome).isInstanceOf(FluxguardRateLimitOutcome.Unavailable.class);
    }

    @Test
    void checkLogin_disabledFlag_shortCircuitsToDisabled_withoutTouchingChannel() {
        service.respondAllow();
        FluxguardRateLimitOutcome outcome =
                client(disabledProps).checkLogin("login-req-4", "203.0.113.7");
        assertThat(outcome).isInstanceOf(FluxguardRateLimitOutcome.Disabled.class);
        assertThat(service.callCount()).isZero();
    }

    @Test
    void reportLoginFailure_callsStubWithClientIp() {
        client(enabledProps).reportLoginFailure("login-req-5", "203.0.113.7");
        assertThat(service.reportCallCount()).isEqualTo(1);
        assertThat(service.lastReportRequest().getRequestId()).isEqualTo("login-req-5");
        assertThat(service.lastReportRequest().getClientIp()).isEqualTo("203.0.113.7");
    }

    @Test
    void reportLoginFailure_disabledFlag_makesNoRpc() {
        client(disabledProps).reportLoginFailure("login-req-6", "203.0.113.7");
        assertThat(service.reportCallCount()).isZero();
    }

    @Test
    void reportLoginFailure_serverError_isSwallowed() {
        service.respondReportError(Status.UNAVAILABLE);
        // Best-effort: a transport error must not propagate to the caller.
        client(enabledProps).reportLoginFailure("login-req-7", "203.0.113.7");
        assertThat(service.reportCallCount()).isEqualTo(1);
    }

    @Test
    void checkOpsRelease_allowResponse_setsPolicyOpsReleaseAndSubject() {
        service.respondAllow();
        FluxguardRateLimitOutcome outcome =
                client(enabledProps).checkOpsRelease("ops-req-1", "support-agent");
        assertThat(outcome).isInstanceOf(FluxguardRateLimitOutcome.Allow.class);
        assertThat(service.lastCheckRequest().getPolicy()).isEqualTo(Policy.POLICY_OPS_RELEASE);
        assertThat(service.lastCheckRequest().getSubject()).isEqualTo("support-agent");
    }

    @Test
    void checkOpsRelease_denyResponse_mapsToDeniedWithRetryAfter() {
        service.respondDeny(2500L);
        FluxguardRateLimitOutcome outcome =
                client(enabledProps).checkOpsRelease("ops-req-2", "support-agent");
        assertThat(outcome).isInstanceOf(FluxguardRateLimitOutcome.Denied.class);
        assertThat(((FluxguardRateLimitOutcome.Denied) outcome).retryAfterMs()).isEqualTo(2500L);
    }

    @Test
    void checkOpsReject_allowResponse_setsPolicyOpsReject() {
        service.respondAllow();
        FluxguardRateLimitOutcome outcome =
                client(enabledProps).checkOpsReject("ops-req-3", "support-agent");
        assertThat(outcome).isInstanceOf(FluxguardRateLimitOutcome.Allow.class);
        assertThat(service.lastCheckRequest().getPolicy()).isEqualTo(Policy.POLICY_OPS_REJECT);
    }

    @Test
    void checkOpsRelease_serverError_mapsToUnavailable_failOpen() {
        service.respondError(Status.UNAVAILABLE);
        FluxguardRateLimitOutcome outcome =
                client(enabledProps).checkOpsRelease("ops-req-4", "support-agent");
        assertThat(outcome).isInstanceOf(FluxguardRateLimitOutcome.Unavailable.class);
    }

    @Test
    void checkOpsReject_disabledFlag_shortCircuitsWithoutTouchingChannel() {
        service.respondAllow();
        FluxguardRateLimitOutcome outcome =
                client(disabledProps).checkOpsReject("ops-req-5", "support-agent");
        assertThat(outcome).isInstanceOf(FluxguardRateLimitOutcome.Disabled.class);
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
        private CheckLimitRequest lastCheckRequest;
        private Throwable reportError;
        private int reportCallCount;
        private ReportLoginFailureRequest lastReportRequest;

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

        void respondReportError(Status status) {
            this.reportError = status.asRuntimeException();
        }

        int callCount() {
            return callCount;
        }

        int reportCallCount() {
            return reportCallCount;
        }

        CheckLimitRequest lastCheckRequest() {
            return lastCheckRequest;
        }

        ReportLoginFailureRequest lastReportRequest() {
            return lastReportRequest;
        }

        @Override
        public void checkLimit(CheckLimitRequest request,
                StreamObserver<CheckLimitResponse> responseObserver) {
            callCount++;
            lastCheckRequest = request;
            if (error != null) {
                responseObserver.onError(error);
                return;
            }
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void reportLoginFailure(ReportLoginFailureRequest request,
                StreamObserver<ReportLoginFailureResponse> responseObserver) {
            reportCallCount++;
            lastReportRequest = request;
            if (reportError != null) {
                responseObserver.onError(reportError);
                return;
            }
            responseObserver.onNext(ReportLoginFailureResponse.newBuilder().build());
            responseObserver.onCompleted();
        }
    }
}
