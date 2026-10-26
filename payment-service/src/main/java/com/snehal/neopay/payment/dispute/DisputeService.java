package com.snehal.neopay.payment.dispute;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.EventAggregateType;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.common.exception.BusinessRuleViolationException;
import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.payment.entity.Payment;
import com.snehal.neopay.payment.ledger.LedgerAccount;
import com.snehal.neopay.payment.ledger.LedgerReference;
import com.snehal.neopay.payment.ledger.LedgerService;
import com.snehal.neopay.payment.outbox.OutboxEventPublisher;
import com.snehal.neopay.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisputeService {

    private final DisputeRepository disputeRepository;
    private final PaymentRepository paymentRepository;
    private final LedgerService ledgerService;
    private final OutboxEventPublisher eventPublisher;

    @Transactional
    public Dispute open(UUID merchantId, UUID paymentId, CreateDisputeRequest request) {
        Payment payment = paymentRepository.findByIdAndMerchantId(paymentId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        requireDisputable(payment);
        requireAmount(payment, request.amount());

        LedgerAccount source = payment.getStatus() == PaymentStatus.SETTLED
                ? LedgerAccount.BANK_SETTLEMENT
                : LedgerAccount.MERCHANT_PAYABLE;

        Dispute dispute = disputeRepository.save(Dispute.builder()
                .paymentId(paymentId)
                .merchantId(merchantId)
                .amount(request.amount())
                .status(DisputeStatus.OPENED)
                .sourceAccount(source)
                .reason(request.reason())
                .openedAt(LocalDateTime.now())
                .build());

        ledgerService.postPair(dispute.getId(), source, LedgerAccount.DISPUTE_HOLD,
                request.amount(), LedgerReference.dispute(dispute.getId()));
        publish(dispute, "DISPUTE_OPENED");
        return dispute;
    }

    @Transactional
    public Dispute resolve(UUID disputeId, DisputeStatus outcome) {
        Dispute dispute = disputeRepository.findByIdForUpdate(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute", disputeId));
        if (dispute.getStatus() == DisputeStatus.WON || dispute.getStatus() == DisputeStatus.LOST) {
            throw new BusinessRuleViolationException("DISPUTE_ALREADY_RESOLVED",
                    "Dispute is already resolved");
        }

        LedgerAccount credit = outcome == DisputeStatus.WON
                ? dispute.getSourceAccount()
                : LedgerAccount.PLATFORM_RECEIVABLE;

        ledgerService.postPair(UUID.randomUUID(), LedgerAccount.DISPUTE_HOLD, credit,
                dispute.getAmount(), LedgerReference.dispute(dispute.getId()));

        dispute.resolve(outcome, LocalDateTime.now());
        dispute = disputeRepository.save(dispute);
        publish(dispute, "DISPUTE_RESOLVED");
        return dispute;
    }

    @Transactional(readOnly = true)
    public List<Dispute> list(UUID merchantId, UUID paymentId) {
        return disputeRepository.findByPaymentIdAndMerchantIdOrderByOpenedAtAsc(paymentId, merchantId);
    }

    private static void requireDisputable(Payment payment) {
        PaymentStatus status = payment.getStatus();
        boolean disputable = status == PaymentStatus.CAPTURED
                || status == PaymentStatus.PARTIALLY_REFUNDED
                || status == PaymentStatus.SETTLED;
        if (!disputable) {
            throw new BusinessRuleViolationException("PAYMENT_NOT_DISPUTABLE",
                    "Payment cannot be disputed in status: " + status);
        }
    }

    private static void requireAmount(Payment payment, Money amount) {
        if (amount == null || amount.getAmountUnits() <= 0) {
            throw new BusinessRuleViolationException("INVALID_DISPUTE_AMOUNT",
                    "Dispute amount must be positive");
        }
        if (amount.isGreaterThan(payment.getAmount())) {
            throw new BusinessRuleViolationException("DISPUTE_EXCEEDS_CAPTURED",
                    "Dispute amount exceeds captured amount");
        }
    }

    private void publish(Dispute dispute, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("disputeId", dispute.getId().toString());
        payload.put("paymentId", dispute.getPaymentId().toString());
        payload.put("merchantId", dispute.getMerchantId().toString());
        payload.put("amountUnits", dispute.getAmount().getAmountUnits());
        payload.put("amountCurrency", dispute.getAmount().getCurrency());
        payload.put("disputeStatus", dispute.getStatus().name());
        eventPublisher.publish(EventAggregateType.PAYMENT, dispute.getId(), eventType, payload);
    }
}
