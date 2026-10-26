package com.snehal.neopay.payment.saga;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.OrderStatus;
import com.snehal.neopay.common.enums.PaymentEvent;
import com.snehal.neopay.common.enums.PaymentMethod;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.common.exception.BusinessRuleViolationException;
import com.snehal.neopay.common.exception.IdempotencyConflictException;
import com.snehal.neopay.payment.dto.request.PaymentInitRequest;
import com.snehal.neopay.payment.entity.OrderRecord;
import com.snehal.neopay.payment.entity.Payment;
import com.snehal.neopay.payment.fx.FxRateLockService;
import com.snehal.neopay.payment.gateway.dto.PaymentResult;
import com.snehal.neopay.payment.mapper.PaymentMapper;
import com.snehal.neopay.payment.metrics.PaymentMetrics;
import com.snehal.neopay.payment.outbox.OutboxEventPublisher;
import com.snehal.neopay.payment.repository.OrderRepository;
import com.snehal.neopay.payment.repository.PaymentRepository;
import com.snehal.neopay.payment.risk.RiskEnforcer;
import com.snehal.neopay.payment.statemachine.PaymentTransitionService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentAuthorizationRecorderTest {

    private static final UUID MERCHANT = UUID.randomUUID();

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentTransitionService transitionService = mock(PaymentTransitionService.class);
    private final OutboxEventPublisher eventPublisher = mock(OutboxEventPublisher.class);
    private final PaymentMapper paymentMapper = mock(PaymentMapper.class);
    private final PaymentMetrics metrics = mock(PaymentMetrics.class);
    private final RiskEnforcer riskEnforcer = mock(RiskEnforcer.class);
    private final FxRateLockService fxRateLockService = mock(FxRateLockService.class);

    private final PaymentAuthorizationRecorder recorder = new PaymentAuthorizationRecorder(
            orderRepository, paymentRepository, transitionService, eventPublisher,
            paymentMapper, metrics, riskEnforcer, fxRateLockService);

    private static OrderRecord order(LocalDateTime expiresAt) {
        return OrderRecord.builder()
                .id(UUID.randomUUID())
                .merchantId(MERCHANT)
                .amount(Money.inr(1_000))
                .receipt("receipt-1")
                .orderStatus(OrderStatus.CREATED)
                .attempts(0)
                .expiresAt(expiresAt)
                .build();
    }

    private static PaymentInitRequest request(UUID orderId) {
        return new PaymentInitRequest(orderId, PaymentMethod.UPI, Map.of("vpa", "success@okaxis"));
    }

    @Test
    void expiredOrderIsRejectedBeforeAnyPaymentIsPersisted() {
        OrderRecord expired = order(LocalDateTime.now().minusMinutes(1));
        when(orderRepository.findByIdAndMerchantIdForUpdate(expired.getId(), MERCHANT))
                .thenReturn(Optional.of(expired));

        BusinessRuleViolationException error = assertThrows(BusinessRuleViolationException.class,
                () -> recorder.recordPayment(MERCHANT, request(expired.getId()), "key-1"));

        assertEquals("ORDER_EXPIRED", error.getErrorCode());
        verify(paymentRepository, never()).save(any());
        verify(riskEnforcer, never()).enforce(any(), any());
    }

    @Test
    void orderWithoutExpiryIsAccepted() {
        OrderRecord open = order(null);
        when(orderRepository.findByIdAndMerchantIdForUpdate(open.getId(), MERCHANT))
                .thenReturn(Optional.of(open));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        Payment payment = recorder.recordPayment(MERCHANT, request(open.getId()), "key-2");

        assertEquals(PaymentStatus.CREATED, payment.getStatus());
        verify(riskEnforcer).enforce(MERCHANT, open.getAmount());
        verify(transitionService).apply(payment, PaymentEvent.AUTHORIZE_ATTEMPT);
    }

    @Test
    void synchronousGatewaySuccessAuthorizesThePayment() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .merchantId(MERCHANT)
                .order(order(LocalDateTime.now().plusMinutes(30)))
                .amount(Money.inr(1_000))
                .status(PaymentStatus.AUTHORIZING)
                .method(PaymentMethod.UPI)
                .build();
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        recorder.applyGatewayResult(paymentId, new PaymentResult.Success("bank-ref-9"));

        verify(transitionService).apply(payment, PaymentEvent.AUTHORIZE_SUCCESS);
        assertEquals("bank-ref-9", payment.getBankReference());
        assertNotNull(payment.getAuthorizedAt());
    }

    @Test
    void replayWithDifferentPayloadIsRejected() {
        OrderRecord stored = order(LocalDateTime.now().plusMinutes(30));
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .merchantId(MERCHANT)
                .order(stored)
                .amount(Money.inr(1_000))
                .status(PaymentStatus.CREATED)
                .method(PaymentMethod.UPI)
                .methodDetails(Map.of("vpa", "other@okaxis"))
                .build();
        when(paymentRepository.findByMerchantIdAndIdempotencyKey(MERCHANT, "key-3"))
                .thenReturn(Optional.of(payment));

        assertThrows(IdempotencyConflictException.class,
                () -> recorder.findExistingAttempt(MERCHANT, "key-3", request(stored.getId())));
    }

    @Test
    void replayWithSamePayloadReturnsTheStoredAttempt() {
        OrderRecord stored = order(LocalDateTime.now().plusMinutes(30));
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .merchantId(MERCHANT)
                .order(stored)
                .amount(Money.inr(1_000))
                .status(PaymentStatus.CREATED)
                .method(PaymentMethod.UPI)
                .methodDetails(Map.of("vpa", "success@okaxis"))
                .build();
        when(paymentRepository.findByMerchantIdAndIdempotencyKey(MERCHANT, "key-4"))
                .thenReturn(Optional.of(payment));

        com.snehal.neopay.payment.dto.response.PaymentResponse mapped =
                new com.snehal.neopay.payment.dto.response.PaymentResponse(payment.getId(),
                        stored.getId(), MERCHANT, Money.inr(1_000), PaymentStatus.CREATED,
                        PaymentMethod.UPI, Map.of("vpa", "success@okaxis"),
                        null, null, null, null);
        when(paymentMapper.toResponse(payment)).thenReturn(mapped);

        assertEquals(Optional.of(mapped),
                recorder.findExistingAttempt(MERCHANT, "key-4", request(stored.getId())));
    }
}
