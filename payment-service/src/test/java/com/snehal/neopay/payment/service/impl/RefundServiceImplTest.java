package com.snehal.neopay.payment.service.impl;

import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.common.domain.PaymentId;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.EventAggregateType;
import com.snehal.neopay.common.enums.PaymentEvent;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.common.enums.RefundStatus;
import com.snehal.neopay.common.exception.BusinessRuleViolationException;
import com.snehal.neopay.payment.dto.request.CreateRefundRequest;
import com.snehal.neopay.payment.dto.response.RefundResponse;
import com.snehal.neopay.payment.entity.Payment;
import com.snehal.neopay.payment.entity.Refund;
import com.snehal.neopay.payment.ledger.LedgerService;
import com.snehal.neopay.payment.metrics.PaymentMetrics;
import com.snehal.neopay.payment.outbox.OutboxEventPublisher;
import com.snehal.neopay.payment.repository.PaymentRepository;
import com.snehal.neopay.payment.repository.RefundRepository;
import com.snehal.neopay.payment.statemachine.PaymentTransitionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefundServiceImplTest {

    private static final UUID MERCHANT = UUID.randomUUID();

    private PaymentRepository paymentRepository;
    private RefundRepository refundRepository;
    private PaymentTransitionService transitionService;
    private LedgerService ledgerService;
    private OutboxEventPublisher publisher;
    private PaymentMetrics metrics;
    private RefundServiceImpl service;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        refundRepository = mock(RefundRepository.class);
        transitionService = mock(PaymentTransitionService.class);
        ledgerService = mock(LedgerService.class);
        publisher = mock(OutboxEventPublisher.class);
        metrics = mock(PaymentMetrics.class);
        service = new RefundServiceImpl(paymentRepository, refundRepository, transitionService,
                ledgerService, publisher, metrics);

        when(refundRepository.saveAndFlush(any(Refund.class))).thenAnswer(invocation -> {
            Refund refund = invocation.getArgument(0);
            if (refund.getId() == null) {
                ReflectionTestUtils.setField(refund, "id", UUID.randomUUID());
            }
            return refund;
        });
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void partialRefundMovesPaymentToPartiallyRefunded() {
        Payment payment = payment(PaymentStatus.CAPTURED, 1_000);
        when(paymentRepository.findByIdAndMerchantIdForUpdate(payment.getId(), MERCHANT))
                .thenReturn(Optional.of(payment));
        when(refundRepository.sumAmountByPaymentAndStatus(payment.getId(), RefundStatus.PROCESSED))
                .thenReturn(0L);

        RefundResponse response = service.create(MerchantId.of(MERCHANT), PaymentId.of(payment.getId()),
                new CreateRefundRequest(Money.inr(400), "customer request"), "key-1");

        assertEquals(RefundStatus.PROCESSED, response.status());
        assertEquals(400, response.amount().getAmountUnits());
        verify(transitionService).apply(payment, PaymentEvent.REFUND_INIT);
        verify(transitionService, never()).apply(payment, PaymentEvent.REFUND_COMPLETE);
        verify(ledgerService).postRefund(any(), eq(Money.inr(400)), eq(false));
        verify(publisher).publish(eq(EventAggregateType.REFUND), any(), eq("REFUND_PROCESSED"), any());
        verify(metrics).refundProcessed();
    }

    @Test
    void fullRefundCompletesTransition() {
        Payment payment = payment(PaymentStatus.CAPTURED, 1_000);
        when(paymentRepository.findByIdAndMerchantIdForUpdate(payment.getId(), MERCHANT))
                .thenReturn(Optional.of(payment));
        when(refundRepository.sumAmountByPaymentAndStatus(payment.getId(), RefundStatus.PROCESSED))
                .thenReturn(0L);

        service.create(MerchantId.of(MERCHANT), PaymentId.of(payment.getId()),
                new CreateRefundRequest(Money.inr(1_000), "full"), "key-2");

        verify(transitionService).apply(payment, PaymentEvent.REFUND_INIT);
        verify(transitionService).apply(payment, PaymentEvent.REFUND_COMPLETE);
    }

    @Test
    void overRefundIsRejected() {
        Payment payment = payment(PaymentStatus.PARTIALLY_REFUNDED, 1_000);
        when(paymentRepository.findByIdAndMerchantIdForUpdate(payment.getId(), MERCHANT))
                .thenReturn(Optional.of(payment));
        when(refundRepository.sumAmountByPaymentAndStatus(payment.getId(), RefundStatus.PROCESSED))
                .thenReturn(800L);

        assertThrows(BusinessRuleViolationException.class,
                () -> service.create(MerchantId.of(MERCHANT), PaymentId.of(payment.getId()),
                        new CreateRefundRequest(Money.inr(300), "too much"), "key-3"));
        verify(ledgerService, never()).postRefund(any(), any(), eq(false));
    }

    @Test
    void zeroAmountIsRejected() {
        Payment payment = payment(PaymentStatus.CAPTURED, 1_000);
        when(paymentRepository.findByIdAndMerchantIdForUpdate(payment.getId(), MERCHANT))
                .thenReturn(Optional.of(payment));

        assertThrows(BusinessRuleViolationException.class,
                () -> service.create(MerchantId.of(MERCHANT), PaymentId.of(payment.getId()),
                        new CreateRefundRequest(Money.inr(0), "zero"), "key-4"));
    }

    @Test
    void nonRefundableStatusIsRejected() {
        Payment payment = payment(PaymentStatus.AUTHORIZED, 1_000);
        when(paymentRepository.findByIdAndMerchantIdForUpdate(payment.getId(), MERCHANT))
                .thenReturn(Optional.of(payment));

        assertThrows(BusinessRuleViolationException.class,
                () -> service.create(MerchantId.of(MERCHANT), PaymentId.of(payment.getId()),
                        new CreateRefundRequest(Money.inr(100), "early"), "key-5"));
    }

    @Test
    void replayReturnsExistingRefund() {
        Payment payment = payment(PaymentStatus.CAPTURED, 1_000);
        Refund existing = Refund.builder()
                .id(UUID.randomUUID())
                .payment(payment)
                .merchantId(MERCHANT)
                .amount(Money.inr(400))
                .idempotencyKey("key-6")
                .status(RefundStatus.PROCESSED)
                .build();
        when(paymentRepository.findByIdAndMerchantIdForUpdate(payment.getId(), MERCHANT))
                .thenReturn(Optional.of(payment));
        when(refundRepository.findByPayment_IdAndIdempotencyKey(payment.getId(), "key-6"))
                .thenReturn(Optional.of(existing));

        RefundResponse response = service.create(MerchantId.of(MERCHANT), PaymentId.of(payment.getId()),
                new CreateRefundRequest(Money.inr(400), "replay"), "key-6");

        assertEquals(existing.getId(), response.id());
        verify(ledgerService, never()).postRefund(any(), any(), eq(false));
        verify(metrics).refundReplayed();
    }

    @Test
    void settledPaymentUsesSettlementAccount() {
        Payment payment = payment(PaymentStatus.SETTLED, 1_000);
        payment.markSettled(java.time.LocalDateTime.now());
        when(paymentRepository.findByIdAndMerchantIdForUpdate(payment.getId(), MERCHANT))
                .thenReturn(Optional.of(payment));
        when(refundRepository.sumAmountByPaymentAndStatus(payment.getId(), RefundStatus.PROCESSED))
                .thenReturn(0L);

        service.create(MerchantId.of(MERCHANT), PaymentId.of(payment.getId()),
                new CreateRefundRequest(Money.inr(100), "after settlement"), "key-7");

        verify(ledgerService).postRefund(any(), any(), eq(true));
    }

    private static Payment payment(PaymentStatus status, int units) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .merchantId(MERCHANT)
                .amount(Money.inr(units))
                .idempotencyKey("pay-" + UUID.randomUUID())
                .status(status)
                .method(com.snehal.neopay.common.enums.PaymentMethod.UPI)
                .build();
    }
}
