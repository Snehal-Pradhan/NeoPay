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

/**
 * Dispute lifecycle: open, request evidence, accept evidence, represent, resolve.
 *
 * <p>Money moves exactly twice: on open (source account → {@code DISPUTE_HOLD}) and on a
 * terminal outcome ({@code DISPUTE_HOLD} → source for WON, {@code PLATFORM_RECEIVABLE} for
 * LOST). Evidence and representment are state-only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DisputeService {

    static final String EVIDENCE_DEADLINE_MISSED = "EVIDENCE_DEADLINE_MISSED";

    private final DisputeRepository disputeRepository;
    private final PaymentRepository paymentRepository;
    private final DisputeEvidenceRepository evidenceRepository;
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
    public Dispute requestEvidence(UUID merchantId, UUID disputeId, LocalDateTime deadline) {
        Dispute dispute = requireOpenDispute(merchantId, disputeId);
        if (dispute.getStatus() == DisputeStatus.REPRESENTED) {
            throw new BusinessRuleViolationException("DISPUTE_ALREADY_REPRESENTED",
                    "Dispute has already been represented");
        }
        dispute.requestEvidence(deadline);
        dispute = disputeRepository.save(dispute);
        publish(dispute, "DISPUTE_EVIDENCE_REQUESTED");
        return dispute;
    }

    @Transactional
    public Dispute submitEvidence(UUID merchantId, UUID disputeId, EvidenceRequest request) {
        Dispute dispute = requireOpenDispute(merchantId, disputeId);
        LocalDateTime now = LocalDateTime.now();

        if (dispute.evidenceWindowClosed(now)) {
            throw new BusinessRuleViolationException("EVIDENCE_WINDOW_CLOSED",
                    "Evidence deadline has passed");
        }
        if (evidenceRepository.findByDisputeIdAndExternalEvidenceId(disputeId,
                request.externalEvidenceId()).isPresent()) {
            log.info("Evidence {} already recorded for dispute {}",
                    request.externalEvidenceId(), disputeId);
            return dispute;
        }

        evidenceRepository.save(DisputeEvidence.builder()
                .disputeId(disputeId)
                .externalEvidenceId(request.externalEvidenceId())
                .summary(request.summary())
                .submittedAt(now)
                .build());
        dispute.submitEvidence(request.externalEvidenceId(), now);
        dispute = disputeRepository.save(dispute);
        publish(dispute, "DISPUTE_EVIDENCE_SUBMITTED");
        return dispute;
    }

    @Transactional
    public Dispute represent(UUID merchantId, UUID disputeId, String statement) {
        Dispute dispute = requireOpenDispute(merchantId, disputeId);
        if (dispute.getEvidenceSubmittedAt() == null) {
            throw new BusinessRuleViolationException("EVIDENCE_NOT_SUBMITTED",
                    "Evidence must be submitted before representment");
        }
        dispute.represent();
        dispute = disputeRepository.save(dispute);
        publish(dispute, "DISPUTE_REPRESENTED");
        return dispute;
    }

    @Transactional
    public Dispute resolve(UUID merchantId, UUID disputeId, DisputeStatus outcome, String reason) {
        Dispute dispute = requireOpenDispute(merchantId, disputeId);
        return resolveInternal(dispute, outcome, reason);
    }

    @Transactional
    public Dispute resolve(UUID disputeId, DisputeStatus outcome) {
        Dispute dispute = disputeRepository.findByIdForUpdate(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute", disputeId));
        return resolveInternal(dispute, outcome, null);
    }

    /**
     * Closes every dispute whose evidence deadline passed without a submission as LOST.
     * Running twice is safe: closed disputes no longer match the query.
     *
     * @return number of disputes closed
     */
    @Transactional
    public int closeExpiredEvidenceWindows(LocalDateTime now) {
        List<Dispute> expired = disputeRepository
                .findByStatusAndEvidenceDueAtBefore(DisputeStatus.EVIDENCE_REQUIRED, now);
        for (Dispute dispute : expired) {
            resolveInternal(dispute, DisputeStatus.LOST, EVIDENCE_DEADLINE_MISSED);
        }
        return expired.size();
    }

    @Transactional(readOnly = true)
    public List<Dispute> list(UUID merchantId, UUID paymentId) {
        return disputeRepository.findByPaymentIdAndMerchantIdOrderByOpenedAtAsc(paymentId, merchantId);
    }

    private Dispute requireOpenDispute(UUID merchantId, UUID disputeId) {
        Dispute dispute = disputeRepository.findByIdForUpdate(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute", disputeId));
        if (!dispute.getMerchantId().equals(merchantId)) {
            throw new ResourceNotFoundException("Dispute", disputeId);
        }
        if (dispute.getStatus().isTerminal()) {
            throw new BusinessRuleViolationException("DISPUTE_ALREADY_RESOLVED",
                    "Dispute is already resolved");
        }
        return dispute;
    }

    private Dispute resolveInternal(Dispute dispute, DisputeStatus outcome, String reason) {
        if (dispute.getStatus().isTerminal()) {
            throw new BusinessRuleViolationException("DISPUTE_ALREADY_RESOLVED",
                    "Dispute is already resolved");
        }
        LedgerAccount credit = outcome == DisputeStatus.WON
                ? dispute.getSourceAccount()
                : LedgerAccount.PLATFORM_RECEIVABLE;

        ledgerService.postPair(UUID.randomUUID(), LedgerAccount.DISPUTE_HOLD, credit,
                dispute.getAmount(), LedgerReference.dispute(dispute.getId()));

        dispute.resolve(outcome, LocalDateTime.now(), reason);
        dispute = disputeRepository.save(dispute);
        publish(dispute, "DISPUTE_RESOLVED");
        return dispute;
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
