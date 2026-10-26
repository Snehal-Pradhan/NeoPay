package com.snehal.neopay.payment.service.impl;

import com.snehal.neopay.common.dto.PaymentSettlementView;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.PaymentEvent;
import com.snehal.neopay.common.enums.PaymentMethod;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.payment.entity.Payment;
import com.snehal.neopay.payment.repository.PaymentRepository;
import com.snehal.neopay.payment.repository.RefundRepository;
import com.snehal.neopay.common.enums.RefundStatus;
import com.snehal.neopay.payment.fx.FxRateLockService;
import com.snehal.neopay.payment.statemachine.PaymentTransitionService;
import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.common.domain.PaymentId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentLookupServiceImplTest {

    private final PaymentRepository repository = mock(PaymentRepository.class);
    private final RefundRepository refundRepository = mock(RefundRepository.class);
    private final PaymentTransitionService transitionService = mock(PaymentTransitionService.class);
    private final FxRateLockService fxRateLockService = mock(FxRateLockService.class);
    private final PaymentLookupServiceImpl service =
            new PaymentLookupServiceImpl(repository, refundRepository, transitionService, fxRateLockService);

    @Test
    void unsettledCapturedPaymentsMapToSettlementView() {
        UUID merchantId = UUID.randomUUID();
        Payment payment = payment(PaymentStatus.CAPTURED);
        when(repository.findByMerchantIdAndStatusInForUpdate(merchantId,
                List.of(PaymentStatus.CAPTURED, PaymentStatus.PARTIALLY_REFUNDED)))
                .thenReturn(List.of(payment));

        List<PaymentSettlementView> views = service.findUnsettledCapturedPayments(MerchantId.of(merchantId));

        assertEquals(1, views.size());
        assertEquals(payment.getId(), views.getFirst().paymentId());
        assertEquals(500, views.getFirst().amountUnits());
        assertEquals(0, views.getFirst().refundedAmountUnits());
        assertEquals("INR", views.getFirst().currency());
    }

    @Test
    void markSettledTransitionsAndStampsTime() {
        Payment payment = payment(PaymentStatus.CAPTURED);
        when(repository.findAllById(List.of(payment.getId()))).thenReturn(List.of(payment));
        when(repository.saveAll(any())).thenReturn(List.of(payment));

        service.markSettled(List.of(PaymentId.of(payment.getId())));

        verify(transitionService).apply(payment, PaymentEvent.SETTLE);
        assertNotNull(payment.getSettledAt());
        verify(repository).saveAll(any());
    }

    @Test
    void emptyListIsNoOp() {
        when(repository.findAllById(List.of())).thenReturn(List.of());

        service.markSettled(List.of());

        verify(repository).saveAll(eq(List.of()));
    }

    private static Payment payment(PaymentStatus status) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .amount(Money.inr(500))
                .status(status)
                .method(PaymentMethod.UPI)
                .build();
    }

    @Test
    void nonInrPaymentIsConvertedToSettlementCurrency() {
        UUID merchantId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .merchantId(merchantId)
                .amount(Money.of(100, "USD"))
                .status(PaymentStatus.CAPTURED)
                .method(PaymentMethod.UPI)
                .build();
        when(repository.findByMerchantIdAndStatusInForUpdate(merchantId,
                List.of(PaymentStatus.CAPTURED, PaymentStatus.PARTIALLY_REFUNDED)))
                .thenReturn(List.of(payment));
        when(fxRateLockService.require(payment.getId())).thenReturn(
                com.snehal.neopay.payment.fx.FxRateLock.builder()
                        .paymentId(payment.getId())
                        .baseCurrency("USD")
                        .quoteCurrency("INR")
                        .rate(new java.math.BigDecimal("83.00"))
                        .spreadBps(0)
                        .expiresAt(java.time.LocalDateTime.now().plusMinutes(5))
                        .createdAt(java.time.LocalDateTime.now())
                        .build());

        List<PaymentSettlementView> views = service.findUnsettledCapturedPayments(
                com.snehal.neopay.common.domain.MerchantId.of(merchantId));

        assertEquals(8_300, views.getFirst().settlementAmountUnits());
        assertEquals("INR", views.getFirst().settlementCurrency());
    }

    @Test
    void partiallyRefundedPaymentSettlesTheNetAmount() {
        UUID merchantId = UUID.randomUUID();
        Payment payment = payment(PaymentStatus.PARTIALLY_REFUNDED);
        when(repository.findByMerchantIdAndStatusInForUpdate(merchantId,
                List.of(PaymentStatus.CAPTURED, PaymentStatus.PARTIALLY_REFUNDED)))
                .thenReturn(List.of(payment));
        when(refundRepository.sumAmountByPaymentAndStatus(payment.getId(), RefundStatus.PROCESSED))
                .thenReturn(200L);

        PaymentSettlementView view = service.findUnsettledCapturedPayments(MerchantId.of(merchantId))
                .getFirst();

        assertEquals(500, view.amountUnits());
        assertEquals(200, view.refundedAmountUnits());
        assertEquals(300, view.settlementAmountUnits());
    }

    @Test
    void nonInrSettlementConvertsTheNetOfRefunds() {
        UUID merchantId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .merchantId(merchantId)
                .amount(Money.of(100, "USD"))
                .status(PaymentStatus.PARTIALLY_REFUNDED)
                .method(PaymentMethod.UPI)
                .build();
        when(repository.findByMerchantIdAndStatusInForUpdate(merchantId,
                List.of(PaymentStatus.CAPTURED, PaymentStatus.PARTIALLY_REFUNDED)))
                .thenReturn(List.of(payment));
        when(refundRepository.sumAmountByPaymentAndStatus(payment.getId(), RefundStatus.PROCESSED))
                .thenReturn(40L);
        when(fxRateLockService.require(payment.getId())).thenReturn(
                com.snehal.neopay.payment.fx.FxRateLock.builder()
                        .paymentId(payment.getId())
                        .baseCurrency("USD")
                        .quoteCurrency("INR")
                        .rate(new java.math.BigDecimal("83.00"))
                        .spreadBps(0)
                        .expiresAt(java.time.LocalDateTime.now().plusMinutes(5))
                        .createdAt(java.time.LocalDateTime.now())
                        .build());

        PaymentSettlementView view = service.findUnsettledCapturedPayments(MerchantId.of(merchantId))
                .getFirst();

        assertEquals(4_980, view.settlementAmountUnits());
    }
}
