package com.snehal.neopay.payment.dispute;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.EventAggregateType;
import com.snehal.neopay.common.enums.PaymentMethod;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.common.exception.BusinessRuleViolationException;
import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.payment.entity.Payment;
import com.snehal.neopay.payment.ledger.LedgerAccount;
import com.snehal.neopay.payment.ledger.LedgerService;
import com.snehal.neopay.payment.outbox.OutboxEventPublisher;
import com.snehal.neopay.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
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
import static org.mockito.Mockito.when;

/** Publish and validation branches of {@link DisputeService}. */
class DisputeServiceBehaviorTest {

    private static final UUID MERCHANT = UUID.randomUUID();

    private DisputeRepository disputeRepository;
    private PaymentRepository paymentRepository;
    private LedgerService ledgerService;
    private OutboxEventPublisher eventPublisher;
    private DisputeService service;

    @BeforeEach
    void setUp() {
        disputeRepository = mock(DisputeRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        ledgerService = mock(LedgerService.class);
        eventPublisher = mock(OutboxEventPublisher.class);
        service = new DisputeService(disputeRepository, paymentRepository, ledgerService,
                eventPublisher);
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(invocation -> {
            Dispute dispute = invocation.getArgument(0);
            if (dispute.getId() == null) {
                ReflectionTestUtils.setField(dispute, "id", UUID.randomUUID());
            }
            return dispute;
        });
    }

    @Test
    void openingPublishesDisputeOpened() {
        Payment payment = payment(PaymentStatus.CAPTURED, 1_000);
        when(paymentRepository.findByIdAndMerchantId(payment.getId(), MERCHANT))
                .thenReturn(Optional.of(payment));

        Dispute dispute = service.open(MERCHANT, payment.getId(),
                new CreateDisputeRequest(Money.inr(300), "fraud"));

        verify(eventPublisher).publish(eq(EventAggregateType.PAYMENT), eq(dispute.getId()),
                eq("DISPUTE_OPENED"), any());
    }

    @Test
    void zeroAmountIsRejectedBeforeAnyLedgerWrite() {
        Payment payment = payment(PaymentStatus.CAPTURED, 1_000);
        when(paymentRepository.findByIdAndMerchantId(payment.getId(), MERCHANT))
                .thenReturn(Optional.of(payment));

        BusinessRuleViolationException error = assertThrows(BusinessRuleViolationException.class,
                () -> service.open(MERCHANT, payment.getId(),
                        new CreateDisputeRequest(Money.inr(0), "zero")));

        assertEquals("INVALID_DISPUTE_AMOUNT", error.getErrorCode());
        verify(ledgerService, never()).postPair(any(), any(), any(), any(), any());
    }

    @Test
    void fullCapturedAmountIsAccepted() {
        Payment payment = payment(PaymentStatus.CAPTURED, 1_000);
        when(paymentRepository.findByIdAndMerchantId(payment.getId(), MERCHANT))
                .thenReturn(Optional.of(payment));

        Dispute dispute = service.open(MERCHANT, payment.getId(),
                new CreateDisputeRequest(Money.inr(1_000), "full"));

        assertEquals(1_000, dispute.getAmount().getAmountUnits());
    }

    @Test
    void partiallyRefundedPaymentIsDisputable() {
        Payment payment = payment(PaymentStatus.PARTIALLY_REFUNDED, 1_000);
        when(paymentRepository.findByIdAndMerchantId(payment.getId(), MERCHANT))
                .thenReturn(Optional.of(payment));

        assertNotNull(service.open(MERCHANT, payment.getId(),
                new CreateDisputeRequest(Money.inr(100), "partial")));
    }

    @Test
    void nonDisputablePaymentIsRejected() {
        Payment payment = payment(PaymentStatus.AUTHORIZED, 1_000);
        when(paymentRepository.findByIdAndMerchantId(payment.getId(), MERCHANT))
                .thenReturn(Optional.of(payment));

        BusinessRuleViolationException error = assertThrows(BusinessRuleViolationException.class,
                () -> service.open(MERCHANT, payment.getId(),
                        new CreateDisputeRequest(Money.inr(100), "too early")));

        assertEquals("PAYMENT_NOT_DISPUTABLE", error.getErrorCode());
        verify(ledgerService, never()).postPair(any(), any(), any(), any(), any());
    }

    @Test
    void missingPaymentIsReported() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findByIdAndMerchantId(paymentId, MERCHANT))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.open(MERCHANT, paymentId,
                new CreateDisputeRequest(Money.inr(100), "missing")));
    }

    @Test
    void resolutionPublishesAndReturnsTheSavedDispute() {
        Dispute dispute = Dispute.builder()
                .id(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .merchantId(MERCHANT)
                .amount(Money.inr(300))
                .status(DisputeStatus.OPENED)
                .sourceAccount(LedgerAccount.MERCHANT_PAYABLE)
                .openedAt(LocalDateTime.now())
                .build();
        when(disputeRepository.findByIdForUpdate(dispute.getId())).thenReturn(Optional.of(dispute));

        Dispute resolved = service.resolve(dispute.getId(), DisputeStatus.LOST);

        assertNotNull(resolved);
        verify(eventPublisher).publish(eq(EventAggregateType.PAYMENT), eq(dispute.getId()),
                eq("DISPUTE_RESOLVED"), any());
    }

    @Test
    void listReturnsRepositoryOrder() {
        UUID paymentId = UUID.randomUUID();
        List<Dispute> disputes = List.of(mock(Dispute.class));
        when(disputeRepository.findByPaymentIdAndMerchantIdOrderByOpenedAtAsc(paymentId, MERCHANT))
                .thenReturn(disputes);

        assertEquals(disputes, service.list(MERCHANT, paymentId));
    }

    private static Payment payment(PaymentStatus status, int units) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .merchantId(MERCHANT)
                .amount(Money.inr(units))
                .idempotencyKey("pay-" + UUID.randomUUID())
                .status(status)
                .method(PaymentMethod.UPI)
                .build();
    }
}
