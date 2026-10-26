package com.snehal.neopay.payment.service.impl;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.PaymentMethod;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.payment.dto.request.PaymentInitRequest;
import com.snehal.neopay.payment.dto.response.PaymentResponse;
import com.snehal.neopay.payment.entity.OrderRecord;
import com.snehal.neopay.payment.entity.Payment;
import com.snehal.neopay.payment.gateway.PaymentGatewayRouter;
import com.snehal.neopay.payment.gateway.dto.PaymentRequest;
import com.snehal.neopay.payment.gateway.dto.PaymentResult;
import com.snehal.neopay.payment.mapper.PaymentMapper;
import com.snehal.neopay.payment.ledger.LedgerService;
import com.snehal.neopay.payment.metrics.PaymentMetrics;
import com.snehal.neopay.payment.outbox.OutboxEventPublisher;
import com.snehal.neopay.payment.repository.OrderRepository;
import com.snehal.neopay.payment.repository.PaymentRepository;
import com.snehal.neopay.payment.saga.PaymentAuthorizationRecorder;
import com.snehal.neopay.payment.statemachine.PaymentTransitionService;
import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.common.domain.PaymentId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceImplTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentGatewayRouter gatewayRouter = mock(PaymentGatewayRouter.class);
    private final PaymentMapper paymentMapper = mock(PaymentMapper.class);
    private final PaymentTransitionService transitionService = mock(PaymentTransitionService.class);
    private final OutboxEventPublisher eventPublisher = mock(OutboxEventPublisher.class);
    private final PaymentAuthorizationRecorder recorder = mock(PaymentAuthorizationRecorder.class);
    private final PaymentMetrics metrics = mock(PaymentMetrics.class);
    private final LedgerService ledgerService = mock(LedgerService.class);

    private final PaymentServiceImpl service = new PaymentServiceImpl(
            orderRepository, paymentRepository, gatewayRouter, paymentMapper,
            transitionService, eventPublisher, recorder, metrics, ledgerService);

    private static final UUID MERCHANT = UUID.randomUUID();
    private static final UUID ORDER = UUID.randomUUID();

    @Test
    void replayReturnsExistingAttemptWithoutRecordingAgain() {
        PaymentResponse existing = response(UUID.randomUUID());
        when(recorder.findExistingAttempt(eq(MERCHANT), eq("key-1"), any())).thenReturn(Optional.of(existing));

        PaymentResponse result = service.initiate(MerchantId.of(MERCHANT), request(), "key-1");

        assertSame(existing, result);
        verify(recorder, never()).recordPayment(any(), any(), any());
        verify(gatewayRouter, never()).initiate(any());
        verify(metrics).idempotencyReplay();
    }

    @Test
    void gatewayExceptionCompensatesInsteadOfPropagating() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .merchantId(MERCHANT)
                .amount(Money.inr(500))
                .status(PaymentStatus.AUTHORIZING)
                .method(PaymentMethod.UPI)
                .build();

        PaymentResponse compensated = response(paymentId);

        when(recorder.findExistingAttempt(eq(MERCHANT), eq("key-2"), any())).thenReturn(Optional.empty());
        when(recorder.recordPayment(eq(MERCHANT), any(PaymentInitRequest.class), eq("key-2"))).thenReturn(payment);
        when(gatewayRouter.initiate(any(PaymentRequest.class))).thenThrow(new RuntimeException("vault down"));
        when(recorder.compensateAuthorizationFailure(
                eq(paymentId), eq("PAYMENT_GATEWAY_ROUTER_UNREACHABLE"), anyString()))
                .thenReturn(compensated);

        PaymentResponse result = service.initiate(MerchantId.of(MERCHANT), request(), "key-2");

        assertSame(compensated, result);
        verify(recorder, never()).applyGatewayResult(any(), any());
    }

    @Test
    void successfulGatewayCallIsApplied() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .merchantId(MERCHANT)
                .amount(Money.inr(500))
                .status(PaymentStatus.AUTHORIZING)
                .method(PaymentMethod.UPI)
                .build();
        PaymentResult pending = new PaymentResult.Pending("UPI_PROCESSOR_ref");
        PaymentResponse applied = response(paymentId);

        when(recorder.findExistingAttempt(eq(MERCHANT), eq("key-3"), any())).thenReturn(Optional.empty());
        when(recorder.recordPayment(eq(MERCHANT), any(PaymentInitRequest.class), eq("key-3"))).thenReturn(payment);
        when(gatewayRouter.initiate(any(PaymentRequest.class))).thenReturn(pending);
        when(recorder.applyGatewayResult(paymentId, pending)).thenReturn(applied);

        assertSame(applied, service.initiate(MerchantId.of(MERCHANT), request(), "key-3"));
        verify(recorder, never()).compensateAuthorizationFailure(any(), any(), any());
    }

    @Test
    void noIdempotencyKeySkipsReplayLookup() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .merchantId(MERCHANT)
                .amount(Money.inr(500))
                .status(PaymentStatus.AUTHORIZING)
                .method(PaymentMethod.UPI)
                .build();

        when(recorder.recordPayment(eq(MERCHANT), any(PaymentInitRequest.class), eq(null))).thenReturn(payment);
        when(gatewayRouter.initiate(any(PaymentRequest.class))).thenReturn(new PaymentResult.Pending("ref"));
        when(recorder.applyGatewayResult(eq(paymentId), any())).thenReturn(response(paymentId));

        service.initiate(MerchantId.of(MERCHANT), request(), null);

        verify(recorder, never()).findExistingAttempt(any(), any(), any());
    }

    private static PaymentInitRequest request() {
        return new PaymentInitRequest(ORDER, PaymentMethod.UPI, Map.of("vpa", "success@okaxis"));
    }

    private static PaymentResponse response(UUID paymentId) {
        return new PaymentResponse(paymentId, ORDER, MERCHANT, Money.inr(500),
                PaymentStatus.AUTHORIZING, PaymentMethod.UPI, Map.of(), null, null, null, null);
    }

    @Test
    void replayIsStableAcrossRepeatedCalls() {
        PaymentResponse existing = response(UUID.randomUUID());
        when(recorder.findExistingAttempt(eq(MERCHANT), eq("same"), any())).thenReturn(Optional.of(existing));

        PaymentResponse first = service.initiate(MerchantId.of(MERCHANT), request(), "same");
        PaymentResponse second = service.initiate(MerchantId.of(MERCHANT), request(), "same");

        assertEquals(first.id(), second.id());
    }

    @Test
    void captureSuccessMarksCapturedAndPublishes() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = authorizedPayment(paymentId);
        PaymentResponse response = response(paymentId);

        when(paymentRepository.findByIdAndMerchantIdForUpdate(paymentId, MERCHANT))
                .thenReturn(Optional.of(payment));
        when(gatewayRouter.capture(PaymentMethod.UPI, paymentId))
                .thenReturn(new PaymentResult.Success("bank-ref"));
        when(paymentRepository.save(payment)).thenReturn(payment);
        when(paymentMapper.toResponse(payment)).thenReturn(response);

        PaymentResponse result = service.capture(MerchantId.of(MERCHANT), PaymentId.of(paymentId));

        assertSame(response, result);
        assertNotNull(payment.getCapturedAt());
        verify(transitionService).apply(payment, com.snehal.neopay.common.enums.PaymentEvent.CAPTURE_SUCCESS);
        verify(eventPublisher).publish(any(), eq(paymentId), eq("PAYMENT_STATUS_CHANGED"), any());
        verify(metrics).paymentCaptured("UPI");
    }

    @Test
    void captureFailureRecordsError() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = authorizedPayment(paymentId);

        when(paymentRepository.findByIdAndMerchantIdForUpdate(paymentId, MERCHANT))
                .thenReturn(Optional.of(payment));
        when(gatewayRouter.capture(PaymentMethod.UPI, paymentId))
                .thenReturn(new PaymentResult.Failure("CAPTURE_DECLINED", "no"));
        when(paymentRepository.save(payment)).thenReturn(payment);
        when(paymentMapper.toResponse(payment)).thenReturn(response(paymentId));

        service.capture(MerchantId.of(MERCHANT), PaymentId.of(paymentId));

        assertEquals("CAPTURE_DECLINED", payment.getErrorCode());
        assertEquals("no", payment.getErrorDescription());
        verify(metrics).paymentFailed("CAPTURE_DECLINED");
    }

    @Test
    void captureMissingPaymentThrows() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findByIdAndMerchantIdForUpdate(paymentId, MERCHANT))
                .thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                com.snehal.neopay.common.exception.ResourceNotFoundException.class,
                () -> service.capture(MerchantId.of(MERCHANT), PaymentId.of(paymentId)));
    }

    private static Payment authorizedPayment(UUID paymentId) {
        OrderRecord order = OrderRecord.builder()
                .id(UUID.randomUUID())
                .merchantId(MERCHANT)
                .amount(Money.inr(500))
                .receipt("r-" + paymentId)
                .orderStatus(com.snehal.neopay.common.enums.OrderStatus.CREATED)
                .attempts(0)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
        return Payment.builder()
                .id(paymentId)
                .order(order)
                .merchantId(MERCHANT)
                .amount(Money.inr(500))
                .idempotencyKey("k-" + paymentId)
                .status(PaymentStatus.AUTHORIZED)
                .method(PaymentMethod.UPI)
                .build();
    }

    @Test
    void captureOnAlreadyCapturedPaymentReplaysResponse() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = authorizedPayment(paymentId);
        payment.transitionTo(PaymentStatus.CAPTURED);
        PaymentResponse response = response(paymentId);

        when(paymentRepository.findByIdAndMerchantIdForUpdate(paymentId, MERCHANT))
                .thenReturn(Optional.of(payment));
        when(paymentMapper.toResponse(payment)).thenReturn(response);

        PaymentResponse result = service.capture(MerchantId.of(MERCHANT), PaymentId.of(paymentId));

        assertSame(response, result);
        verify(gatewayRouter, never()).capture(any(), any());
        verify(ledgerService, never()).postCapture(any(), any());
        verify(metrics).idempotencyReplay();
    }

    @Test
    void captureMarksTheOrderPaid() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = authorizedPayment(paymentId);
        when(paymentRepository.findByIdAndMerchantIdForUpdate(paymentId, MERCHANT))
                .thenReturn(Optional.of(payment));
        when(gatewayRouter.capture(PaymentMethod.UPI, paymentId))
                .thenReturn(new PaymentResult.Success("bank-ref"));
        when(paymentRepository.save(payment)).thenReturn(payment);
        when(paymentMapper.toResponse(payment)).thenReturn(response(paymentId));

        service.capture(MerchantId.of(MERCHANT), PaymentId.of(paymentId));

        assertEquals(com.snehal.neopay.common.enums.OrderStatus.PAID,
                payment.getOrder().getOrderStatus());
    }
}
