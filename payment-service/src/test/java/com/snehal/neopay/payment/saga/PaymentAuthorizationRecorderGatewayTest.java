package com.snehal.neopay.payment.saga;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.Currency;
import com.snehal.neopay.common.enums.EventAggregateType;
import com.snehal.neopay.common.enums.OrderStatus;
import com.snehal.neopay.common.enums.PaymentEvent;
import com.snehal.neopay.common.enums.PaymentMethod;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.common.exception.BusinessRuleViolationException;
import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.payment.dto.request.PaymentInitRequest;
import com.snehal.neopay.payment.dto.response.PaymentResponse;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Gateway-result, compensation and FX branches of {@link PaymentAuthorizationRecorder}. */
class PaymentAuthorizationRecorderGatewayTest {

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

    @Test
    void pendingGatewayResultStoresProcessorReference() {
        Payment payment = payment(PaymentStatus.AUTHORIZING);
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);
        PaymentResponse mapped = response(payment.getId());
        when(paymentMapper.toResponse(payment)).thenReturn(mapped);

        PaymentResponse result = recorder.applyGatewayResult(payment.getId(),
                new PaymentResult.Pending("UPI_REF_1"));

        assertEquals("UPI_REF_1", payment.getProcessorReference());
        assertSame(mapped, result);
        verify(eventPublisher).publish(eq(EventAggregateType.PAYMENT), eq(payment.getId()),
                eq("PAYMENT_CREATED"), any());
    }

    @Test
    void failedGatewayResultRecordsFailure() {
        Payment payment = payment(PaymentStatus.AUTHORIZING);
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);
        when(paymentMapper.toResponse(payment)).thenReturn(response(payment.getId()));

        recorder.applyGatewayResult(payment.getId(),
                new PaymentResult.Failure("UPI_REJECTED", "bad vpa"));

        assertEquals("UPI_REJECTED", payment.getErrorCode());
        assertEquals("bad vpa", payment.getErrorDescription());
        verify(transitionService).apply(payment, PaymentEvent.AUTHORIZE_FAIL);
        verify(metrics).paymentFailed("UPI_REJECTED");
        verify(paymentRepository).save(payment);
    }

    @Test
    void gatewayResultForUnknownPaymentThrows() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> recorder.applyGatewayResult(paymentId, new PaymentResult.Pending("ref")));
    }

    @Test
    void compensationRecordsFailurePublishesAndReturnsMappedResponse() {
        Payment payment = payment(PaymentStatus.AUTHORIZING);
        PaymentResponse mapped = response(payment.getId());
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);
        when(paymentMapper.toResponse(payment)).thenReturn(mapped);

        PaymentResponse result = recorder.compensateAuthorizationFailure(
                payment.getId(), "ROUTER_DOWN", "connection refused");

        assertEquals(mapped, result);
        assertEquals("ROUTER_DOWN", payment.getErrorCode());
        verify(transitionService).apply(payment, PaymentEvent.AUTHORIZE_FAIL);
        verify(metrics).paymentFailed("ROUTER_DOWN");
        verify(eventPublisher).publish(eq(EventAggregateType.PAYMENT), eq(payment.getId()),
                eq("PAYMENT_AUTHORIZATION_COMPENSATED"), any());
    }

    @Test
    void compensationForUnknownPaymentThrows() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> recorder.compensateAuthorizationFailure(paymentId, "X", "y"));
    }

    @Test
    void foreignCurrencyPaymentLocksAnFxRate() {
        OrderRecord order = order(Money.of(1_000, "USD"), OrderStatus.CREATED);
        when(orderRepository.findByIdAndMerchantIdForUpdate(order.getId(), MERCHANT))
                .thenReturn(Optional.of(order));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        Payment payment = recorder.recordPayment(MERCHANT,
                new PaymentInitRequest(order.getId(), PaymentMethod.CARD, Map.of()), null);

        verify(fxRateLockService).lock(payment.getId(), Currency.USD, Currency.INR);
        assertNotNull(payment.getIdempotencyKey());
        assertEquals(OrderStatus.ATTEMPTED, order.getOrderStatus());
        verify(metrics).paymentInitiated("CARD");
        verify(transitionService).apply(payment, PaymentEvent.AUTHORIZE_ATTEMPT);
    }

    @Test
    void nonPayableOrderIsRejected() {
        OrderRecord paid = order(Money.inr(500), OrderStatus.PAID);
        when(orderRepository.findByIdAndMerchantIdForUpdate(paid.getId(), MERCHANT))
                .thenReturn(Optional.of(paid));

        BusinessRuleViolationException error = assertThrows(BusinessRuleViolationException.class,
                () -> recorder.recordPayment(MERCHANT,
                        new PaymentInitRequest(paid.getId(), PaymentMethod.UPI, Map.of()), "k"));

        assertEquals("ORDER_NOT_PAYABLE", error.getErrorCode());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void missingOrderIsReported() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdAndMerchantIdForUpdate(orderId, MERCHANT))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> recorder.recordPayment(MERCHANT,
                new PaymentInitRequest(orderId, PaymentMethod.UPI, Map.of()), "k"));
    }

    @Test
    void absentIdempotencyKeyReturnsEmpty() {
        when(paymentRepository.findByMerchantIdAndIdempotencyKey(MERCHANT, "missing"))
                .thenReturn(Optional.empty());

        assertEquals(Optional.empty(), recorder.findExistingAttempt(MERCHANT, "missing",
                new PaymentInitRequest(UUID.randomUUID(), PaymentMethod.UPI, Map.of())));
    }

    private static OrderRecord order(Money amount, OrderStatus status) {
        return OrderRecord.builder()
                .id(UUID.randomUUID())
                .merchantId(MERCHANT)
                .amount(amount)
                .receipt("r")
                .orderStatus(status)
                .attempts(0)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
    }

    private static Payment payment(PaymentStatus status) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .order(order(Money.inr(1_000), OrderStatus.CREATED))
                .merchantId(MERCHANT)
                .amount(Money.inr(1_000))
                .status(status)
                .method(PaymentMethod.UPI)
                .build();
    }

    private static PaymentResponse response(UUID id) {
        return new PaymentResponse(id, UUID.randomUUID(), MERCHANT, Money.inr(1_000),
                PaymentStatus.AUTHORIZING, PaymentMethod.UPI, Map.of(), null, null, null, null);
    }
}
