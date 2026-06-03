package com.bankops.portal.client.fluxa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bankops.portal.config.FluxaProperties;
import com.bankops.portal.service.LoggingService;
import com.fluxa.fraud.v1.Decision;
import com.fluxa.fraud.v1.EvaluateRequest;
import com.fluxa.fraud.v1.EvaluateResponse;
import com.fluxa.fraud.v1.FraudEvalGrpc;
import com.fluxa.fraud.v1.FraudFlag;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

@ExtendWith(MockitoExtension.class)
class FluxaFraudClientTest {

    private FraudEvalGrpc.FraudEvalBlockingStub stub;
    private LoggingService loggingService;
    private FluxaProperties enabledProps;
    private FluxaProperties disabledProps;

    @BeforeEach
    void setUp() {
        stub = mock(FraudEvalGrpc.FraudEvalBlockingStub.class);
        // withDeadline returns a self-stub so the chained call evaluateTransaction works.
        lenient().when(stub.withDeadline(any())).thenReturn(stub);
        loggingService = mock(LoggingService.class);
        enabledProps = new FluxaProperties(true, false, "localhost", 9095,
                Duration.ofMillis(80),
                FluxaProperties.Failure.FAIL_CLOSED, FluxaProperties.Failure.FAIL_OPEN);
        disabledProps = new FluxaProperties(false, true, "localhost", 9095,
                Duration.ofMillis(80),
                FluxaProperties.Failure.FAIL_CLOSED, FluxaProperties.Failure.FAIL_OPEN);
    }

    @Test
    void disabledFlag_shortCircuitsToDisabled() {
        FluxaFraudClient client = new FluxaFraudClient(stub, disabledProps, loggingService);
        FluxaEvalOutcome outcome = client.evaluate("cid", 1L, BigDecimal.TEN, "USD", "",
                Instant.now());
        assertThat(outcome).isInstanceOf(FluxaEvalOutcome.Disabled.class);
    }

    @Test
    void allowResponse_mapsToAllow() {
        when(stub.evaluateTransaction(any(EvaluateRequest.class))).thenReturn(
                EvaluateResponse.newBuilder().setDecision(Decision.DECISION_ALLOW).build());
        FluxaFraudClient client = new FluxaFraudClient(stub, enabledProps, loggingService);
        FluxaEvalOutcome outcome = client.evaluate("cid", 1L, BigDecimal.TEN, "USD", "",
                Instant.now());
        assertThat(outcome).isInstanceOf(FluxaEvalOutcome.Allow.class);
    }

    @Test
    void flagResponse_mapsToFlagWithDtos() {
        EvaluateResponse resp = EvaluateResponse.newBuilder()
                .setDecision(Decision.DECISION_FLAG)
                .addFlags(FraudFlag.newBuilder()
                        .setRuleName("amount_threshold")
                        .setRuleValue("amount=15000 > 500"))
                .setMlScore(0.78)
                .setEvaluatedBy("fluxa-rules+ml-v1")
                .build();
        when(stub.evaluateTransaction(any(EvaluateRequest.class))).thenReturn(resp);
        FluxaFraudClient client = new FluxaFraudClient(stub, enabledProps, loggingService);
        FluxaEvalOutcome outcome = client.evaluate("cid", 1L, new BigDecimal("15000"), "USD", "",
                Instant.now());
        assertThat(outcome).isInstanceOf(FluxaEvalOutcome.Flag.class);
        FluxaEvalOutcome.Flag flag = (FluxaEvalOutcome.Flag) outcome;
        assertThat(flag.flags()).hasSize(1);
        assertThat(flag.flags().get(0).ruleName()).isEqualTo("amount_threshold");
        assertThat(flag.flags().get(0).ruleValue()).isEqualTo("amount=15000 > 500");
        assertThat(flag.mlScore()).isEqualTo(0.78);
        assertThat(flag.evaluatedBy()).isEqualTo("fluxa-rules+ml-v1");
    }

    @Test
    void flagResponse_withoutMlScore_defaultsToZero() {
        EvaluateResponse resp = EvaluateResponse.newBuilder()
                .setDecision(Decision.DECISION_FLAG)
                .addFlags(FraudFlag.newBuilder().setRuleName("velocity").setRuleValue("x"))
                .build();
        when(stub.evaluateTransaction(any(EvaluateRequest.class))).thenReturn(resp);
        FluxaFraudClient client = new FluxaFraudClient(stub, enabledProps, loggingService);
        FluxaEvalOutcome outcome = client.evaluate("cid", 1L, BigDecimal.TEN, "USD", "",
                Instant.now());
        assertThat(((FluxaEvalOutcome.Flag) outcome).mlScore()).isEqualTo(0.0);
    }

    @Test
    void deadlineExceeded_mapsToTimeout() {
        when(stub.evaluateTransaction(any(EvaluateRequest.class)))
                .thenThrow(new StatusRuntimeException(Status.DEADLINE_EXCEEDED));
        FluxaFraudClient client = new FluxaFraudClient(stub, enabledProps, loggingService);
        FluxaEvalOutcome outcome = client.evaluate("cid", 1L, BigDecimal.TEN, "USD", "",
                Instant.now());
        assertThat(outcome).isInstanceOf(FluxaEvalOutcome.Timeout.class);
    }

    @Test
    void unavailable_mapsToUnavailable() {
        when(stub.evaluateTransaction(any(EvaluateRequest.class)))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
        FluxaFraudClient client = new FluxaFraudClient(stub, enabledProps, loggingService);
        FluxaEvalOutcome outcome = client.evaluate("cid", 1L, BigDecimal.TEN, "USD", "",
                Instant.now());
        assertThat(outcome).isInstanceOf(FluxaEvalOutcome.Unavailable.class);
    }

    @Test
    void invalidArgument_mapsToInvalidArgumentWithDescription() {
        when(stub.evaluateTransaction(any(EvaluateRequest.class)))
                .thenThrow(new StatusRuntimeException(
                        Status.INVALID_ARGUMENT.withDescription("event_id is required")));
        FluxaFraudClient client = new FluxaFraudClient(stub, enabledProps, loggingService);
        FluxaEvalOutcome outcome = client.evaluate("cid", 1L, BigDecimal.TEN, "USD", "",
                Instant.now());
        assertThat(outcome).isInstanceOf(FluxaEvalOutcome.InvalidArgument.class);
        assertThat(((FluxaEvalOutcome.InvalidArgument) outcome).message())
                .isEqualTo("event_id is required");
    }

    @Test
    void evaluate_setsMerchantOnRequest() {
        when(stub.evaluateTransaction(any(EvaluateRequest.class))).thenReturn(
                EvaluateResponse.newBuilder().setDecision(Decision.DECISION_ALLOW).build());
        FluxaFraudClient client = new FluxaFraudClient(stub, enabledProps, loggingService);

        client.evaluate("cid", 1L, BigDecimal.TEN, "USD", "SilkRoadBazaar", Instant.now());

        // Capture in verify (not in when) — capturing during stubbing is a Mockito smell.
        ArgumentCaptor<EvaluateRequest> captor = ArgumentCaptor.forClass(EvaluateRequest.class);
        verify(stub).evaluateTransaction(captor.capture());
        assertThat(captor.getValue().getMerchant()).isEqualTo("SilkRoadBazaar");
    }
}
