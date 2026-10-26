package com.snehal.neopay.payment.dispute;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.PaymentMethod;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.common.exception.BusinessRuleViolationException;
import com.snehal.neopay.payment.entity.Payment;
import com.snehal.neopay.payment.ledger.LedgerAccount;
import com.snehal.neopay.payment.ledger.LedgerService;
import com.snehal.neopay.payment.outbox.OutboxEventPublisher;
import com.snehal.neopay.payment.repository.PaymentRepository;
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

class DisputeServiceTest {

    private static final UUID MERCHANT = UUID.randomUUID();

    private DisputeRepository disputeRepository;
    private PaymentRepository paymentRepository;
    private LedgerService ledgerService;
    private DisputeService service;

    @BeforeEach
    void setUp() {
        disputeRepository = mock(DisputeRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        ledgerService = mock(LedgerService.class);
        service = new DisputeService(disputeRepository, paymentRepository, ledgerService,
                mock(OutboxEventPublisher.class));

        when(disputeRepository.save(any(Dispute.class))).thenAnswer(invocation -> {
            Dispute dispute = invocation.getArgument(0);
            if (dispute.getId() == null) {
                ReflectionTestUtils.setField(dispute, "id", UUID.randomUUID());
            }
            return dispute;
        });
    }

    @Test
    void openingDisputeHoldsMerchantPayable() {
        Payment payment = payment(PaymentStatus.CAPTURED, 1_000);
        when(paymentRepository.findByIdAndMerchantId(payment.getId(), MERCHANT))
                .thenReturn(Optional.of(payment));

        Dispute dispute = service.open(MERCHANT, payment.getId(),
                new CreateDisputeRequest(Money.inr(300), "fraud"));

        assertEquals(DisputeStatus.OPENED, dispute.getStatus());
        assertEquals(LedgerAccount.MERCHANT_PAYABLE, dispute.getSourceAccount());
        verify(ledgerService).postPair(any(), eq(LedgerAccount.MERCHANT_PAYABLE),
                eq(LedgerAccount.DISPUTE_HOLD), eq(Money.inr(300)), any());
    }

    @Test
    void settledPaymentUsesSettlementAccount() {
        Payment payment = payment(PaymentStatus.SETTLED, 1_000);
        when(paymentRepository.findByIdAndMerchantId(payment.getId(), MERCHANT))
                .thenReturn(Optional.of(payment));

        Dispute dispute = service.open(MERCHANT, payment.getId(),
                new CreateDisputeRequest(Money.inr(100), "late"));

        assertEquals(LedgerAccount.BANK_SETTLEMENT, dispute.getSourceAccount());
    }

    @Test
    void amountAboveCapturedIsRejected() {
        Payment payment = payment(PaymentStatus.CAPTURED, 1_000);
        when(paymentRepository.findByIdAndMerchantId(payment.getId(), MERCHANT))
                .thenReturn(Optional.of(payment));

        assertThrows(BusinessRuleViolationException.class, () -> service.open(
                MERCHANT, payment.getId(), new CreateDisputeRequest(Money.inr(1_001), "too much")));
    }

    @Test
    void wonDisputeReversesHold() {
        Dispute dispute = dispute(DisputeStatus.OPENED, LedgerAccount.MERCHANT_PAYABLE, 300);
        when(disputeRepository.findById(dispute.getId())).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(i -> i.getArgument(0));

        service.resolve(dispute.getId(), DisputeStatus.WON);

        assertEquals(DisputeStatus.WON, dispute.getStatus());
        verify(ledgerService).postPair(any(), eq(LedgerAccount.DISPUTE_HOLD),
                eq(LedgerAccount.MERCHANT_PAYABLE), any(), any());
    }

    @Test
    void lostDisputeMovesHoldToPlatformReceivable() {
        Dispute dispute = dispute(DisputeStatus.OPENED, LedgerAccount.MERCHANT_PAYABLE, 300);
        when(disputeRepository.findById(dispute.getId())).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(i -> i.getArgument(0));

        service.resolve(dispute.getId(), DisputeStatus.LOST);

        assertEquals(DisputeStatus.LOST, dispute.getStatus());
        verify(ledgerService).postPair(any(), eq(LedgerAccount.DISPUTE_HOLD),
                eq(LedgerAccount.PLATFORM_RECEIVABLE), any(), any());
    }

    @Test
    void resolvingTwiceIsRejected() {
        Dispute dispute = dispute(DisputeStatus.WON, LedgerAccount.MERCHANT_PAYABLE, 300);
        when(disputeRepository.findById(dispute.getId())).thenReturn(Optional.of(dispute));

        assertThrows(BusinessRuleViolationException.class,
                () -> service.resolve(dispute.getId(), DisputeStatus.LOST));
        verify(ledgerService, never()).postPair(any(), any(), any(), any(), any());
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

    private static Dispute dispute(DisputeStatus status, LedgerAccount source, int units) {
        return Dispute.builder()
                .id(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .merchantId(MERCHANT)
                .amount(Money.inr(units))
                .status(status)
                .sourceAccount(source)
                .openedAt(java.time.LocalDateTime.now())
                .build();
    }
}
