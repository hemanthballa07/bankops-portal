package com.bankops.portal.client.fluxa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fluxa.fraud.v1.Decision;
import com.fluxa.fraud.v1.EvaluateRequest;
import com.fluxa.fraud.v1.EvaluateResponse;
import com.fluxa.fraud.v1.FraudEvalGrpc;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;

/**
 * Verifies the mechanism the production channel relies on: a gRPC client built with
 * {@link GrpcTelemetry}'s client interceptor injects the W3C {@code traceparent} —
 * carrying the active span's trace id — into the outgoing gRPC metadata, so Fluxa's
 * otelgrpc server can join the trace.
 */
class FluxaTracePropagationTest {

    @RegisterExtension
    static final OpenTelemetryExtension OTEL = OpenTelemetryExtension.create();

    private static final String SERVER = "otel-prop-test";

    @Test
    void clientInterceptor_injectsW3CTraceparentCarryingActiveTraceId() throws Exception {
        AtomicReference<Metadata> captured = new AtomicReference<>();
        ServerInterceptor captureHeaders = new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call, Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {
                captured.set(headers);
                return next.startCall(call, headers);
            }
        };

        Server server = InProcessServerBuilder.forName(SERVER).directExecutor()
                .addService(ServerInterceptors.intercept(new AllowService(), captureHeaders))
                .build().start();
        ManagedChannel channel = InProcessChannelBuilder.forName(SERVER).directExecutor()
                .usePlaintext()
                .intercept(GrpcTelemetry.create(OTEL.getOpenTelemetry()).newClientInterceptor())
                .build();

        try {
            FraudEvalGrpc.FraudEvalBlockingStub stub = FraudEvalGrpc.newBlockingStub(channel);
            Tracer tracer = OTEL.getOpenTelemetry().getTracer("test");
            Span span = tracer.spanBuilder("client-call").startSpan();
            String traceId = span.getSpanContext().getTraceId();
            try (Scope scope = span.makeCurrent()) {
                stub.evaluateTransaction(EvaluateRequest.newBuilder()
                        .setEventId("evt").setUserId("1").setMerchant("M").build());
            } finally {
                span.end();
            }

            Metadata md = captured.get();
            assertThat(md).as("server received metadata").isNotNull();
            String traceparent = md.get(Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER));
            assertThat(traceparent)
                    .as("W3C traceparent injected into outgoing gRPC metadata")
                    .isNotNull()
                    .contains(traceId);
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }

    static class AllowService extends FraudEvalGrpc.FraudEvalImplBase {
        @Override
        public void evaluateTransaction(EvaluateRequest request,
                StreamObserver<EvaluateResponse> responseObserver) {
            responseObserver.onNext(EvaluateResponse.newBuilder()
                    .setDecision(Decision.DECISION_ALLOW).build());
            responseObserver.onCompleted();
        }
    }
}
