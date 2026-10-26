package com.snehal.neopay.payment.service.impl;

import com.snehal.neopay.common.domain.PaymentId;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.EventAggregateType;
import com.snehal.neopay.common.enums.OrderStatus;
import com.snehal.neopay.common.enums.PaymentEvent;
import com.snehal.neopay.common.enums.PaymentMethod;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.payment.entity.OrderRecord;
import com.snehal.neopay.payment.entity.Payment;
import com.snehal.neopay.payment.gateway.PaymentGatewayRouter;
import com.snehal.neopay.payment.gateway.dto.PaymentResult;
import com.snehal.neopay.payment.ledger.LedgerService;
import com.snehal.neopay.payment.mapper.PaymentMapper;
import com.snehal.neopay.payment.metrics.PaymentMetrics;
import com.snehal.neopay.payment.outbox.OutboxEventPublisher;
import com.snehal.neopay.payment.repository.OrderRepository;
import com.snehal.neopay.payment.repository.PaymentRepository;
import com.snehal.neopay.payment.saga.PaymentAuthorizationRecorder;
import com.snehal.neopay.payment.statemachine.PaymentTransitionService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the asynchronous bank-callback path and the capture side effects of
 * {@link PaymentServiceImpl}. The happy-path tests live in PaymentServiceImplTest.
 */
class PaymentServiceImplAuthorizationTest {

    private static final UUID MERCHANT = UUID.randomUUID();

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

    @Test
    void approvedAuthorizationAutoCapturesAndMarksOrderPaid() {
        Payment payment = payment(PaymentStatus.AUTHORIZING);
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(gatewayRouter.capture(PaymentMethod.UPI, payment.getId()))
                .thenReturn(new PaymentResult.Success("bank-ref"));

        service.resolveAuthorization(PaymentId.of(payment.getId()), true,
                "bank-ref", null, null);

        assertEquals(OrderStatus.PAID, payment.getOrder().getOrderStatus());
        assertNotNull(payment.getCapturedAt());
        verify(transitionService).apply(payment, PaymentEvent.AUTHORIZE_SUCCESS);
        verify(transitionService).apply(payment, PaymentEvent.CAPTURE_REQUEST);
        verify(transitionService).apply(payment, PaymentEvent.CAPTURE_SUCCESS);
        verify(ledgerService).postCapture(payment.getId(), payment.getAmount());
        verify(paymentRepository).save(payment);
        verify(orderRepository).save(payment.getOrder());
        verify(eventPublisher).publish(eq(EventAggregateType.PAYMENT), eq(payment.getId()),
                eq("PAYMENT_STATUS_CHANGED"), any());
    }

    @Test
    void captureFailureAfterApprovalRecordsTheReason() {
        Payment payment = payment(PaymentStatus.AUTHORIZING);
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(gatewayRouter.capture(PaymentMethod.UPI, payment.getId()))
                .thenReturn(new PaymentResult.Failure("CAPTURE_DECLINED", "no"));

        service.resolveAuthorization(PaymentId.of(payment.getId()), true,
                "bank-ref", null, null);

        assertEquals("CAPTURE_DECLINED", payment.getErrorCode());
        verify(transitionService).apply(payment, PaymentEvent.CAPTURE_FAIL);
        verify(ledgerService, never()).postCapture(any(), any());
        verify(orderRepository).save(payment.getOrder());
    }

    @Test
    void declinedAuthorizationRecordsFailureAndKeepsOrderUnpaid() {
        Payment payment = payment(PaymentStatus.AUTHORIZING);
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));

        service.resolveAuthorization(PaymentId.of(payment.getId()), false,
                null, "AUTH_DECLINED", "issuer declined");

        assertEquals("AUTH_DECLINED", payment.getErrorCode());
        assertEquals("issuer declined", payment.getErrorDescription());
        assertEquals(OrderStatus.CREATED, payment.getOrder().getOrderStatus());
        verify(transitionService).apply(payment, PaymentEvent.AUTHORIZE_FAIL);
        verify(gatewayRouter, never()).capture(any(), any());
        verify(ledgerService, never()).postCapture(any(), any());
    }

    @Test
    void callbackForNonAuthorizingPaymentIsIgnored() {
        Payment payment = payment(PaymentStatus.CAPTURED);
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));

        service.resolveAuthorization(PaymentId.of(payment.getId()), true,
                "bank-ref", null, null);

        verifyNoInteractions(gatewayRouter, ledgerService, eventPublisher);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void callbackForUnknownPaymentThrows() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.resolveAuthorization(
                PaymentId.of(paymentId), true, "ref", null, null));
    }

    private static Payment payment(PaymentStatus status) {
        OrderRecord order = OrderRecord.builder()
                .id(UUID.randomUUID())
                .merchantId(MERCHANT)
                .amount(Money.inr(500))
                .receipt("r")
                .orderStatus(OrderStatus.CREATED)
                .attempts(0)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
        return Payment.builder()
                .id(UUID.randomUUID())
                .order(order)
                .merchantId(MERCHANT)
                .amount(Money.inr(500))
                .idempotencyKey("k")
                .status(status)
                .method(PaymentMethod.UPI)
                .build();
    }
}
