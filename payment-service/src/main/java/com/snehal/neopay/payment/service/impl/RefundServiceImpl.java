package com.snehal.neopay.payment.service.impl;

import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.common.domain.PaymentId;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.EventAggregateType;
import com.snehal.neopay.common.enums.PaymentEvent;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.common.enums.RefundStatus;
import com.snehal.neopay.common.exception.BusinessRuleViolationException;
import com.snehal.neopay.common.exception.IdempotencyConflictException;
import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.payment.dto.request.CreateRefundRequest;
import com.snehal.neopay.payment.dto.response.RefundResponse;
import com.snehal.neopay.payment.entity.Payment;
import com.snehal.neopay.payment.entity.Refund;
import com.snehal.neopay.payment.ledger.LedgerService;
import com.snehal.neopay.payment.metrics.PaymentMetrics;
import com.snehal.neopay.payment.outbox.OutboxEventPublisher;
import com.snehal.neopay.payment.repository.PaymentRepository;
import com.snehal.neopay.payment.repository.RefundRepository;
import com.snehal.neopay.payment.service.RefundService;
import com.snehal.neopay.payment.statemachine.PaymentTransitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
public class RefundServiceImpl implements RefundService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PaymentTransitionService transitionService;
    private final LedgerService ledgerService;
    private final OutboxEventPublisher eventPublisher;
    private final PaymentMetrics metrics;

    @Override
    @Transactional
    public RefundResponse create(MerchantId merchantId, PaymentId paymentId,
                                 CreateRefundRequest request, String idempotencyKey) {
        Payment payment = paymentRepository
                .findByIdAndMerchantIdForUpdate(paymentId.value(), merchantId.value())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId.value()));

        String key = idempotencyKey == null || idempotencyKey.isBlank()
                ? UUID.randomUUID().toString()
                : idempotencyKey;

        var existing = refundRepository.findByPayment_IdAndIdempotencyKey(payment.getId(), key);
        if (existing.isPresent()) {
            if (!existing.get().getAmount().equals(request.amount())) {
                throw new IdempotencyConflictException(
                        "Idempotency key reused with a different refund amount");
            }
            metrics.refundReplayed();
            return toResponse(existing.get());
        }

        Money amount = request.amount();
        requireValidAmount(payment, amount);
        requireRefundable(payment);

        long alreadyRefunded = refundRepository.sumAmountByPaymentAndStatus(
                payment.getId(), RefundStatus.PROCESSED);
        long total = Math.addExact(alreadyRefunded, amount.getAmountUnits());
        if (total > payment.getAmount().getAmountUnits()) {
            throw new BusinessRuleViolationException("REFUND_EXCEEDS_CAPTURED",
                    "Refund total exceeds captured amount");
        }

        boolean settled = payment.getSettledAt() != null;

        Refund refund = persistPending(payment, merchantId, amount, key, request.reason());

        ledgerService.postRefund(refund.getId(), amount, settled);

        applyTransitions(payment, total);

        refund.markProcessed("REFUND_" + refund.getId(), LocalDateTime.now());
        refund = refundRepository.save(refund);
        paymentRepository.save(payment);

        publish(refund, merchantId);
        metrics.refundProcessed();
        log.info("Refund processed refundId={} paymentId={} amount={}",
                refund.getId(), payment.getId(), amount.getAmountUnits());

        return toResponse(refund);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponse> list(MerchantId merchantId, PaymentId paymentId) {
        return refundRepository
                .findByPayment_IdAndMerchantIdOrderByCreatedAtAsc(paymentId.value(), merchantId.value())
                .stream()
                .map(RefundServiceImpl::toResponse)
                .toList();
    }

    private Refund persistPending(Payment payment, MerchantId merchantId, Money amount,
                                  String key, String reason) {
        try {
            return refundRepository.saveAndFlush(Refund.builder()
                    .payment(payment)
                    .merchantId(merchantId.value())
                    .amount(amount)
                    .idempotencyKey(key)
                    .reason(reason)
                    .status(RefundStatus.PENDING)
                    .build());
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            log.warn("Concurrent refund with the same idempotency key, paymentId={}", payment.getId());
            throw new IdempotencyConflictException(
                    "A refund with this idempotency key is already being processed");
        }
    }

    private void applyTransitions(Payment payment, long total) {
        if (payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            transitionService.apply(payment, PaymentEvent.REFUND_INIT);
        }
        if (total == payment.getAmount().getAmountUnits()) {
            transitionService.apply(payment, PaymentEvent.REFUND_COMPLETE);
        }
    }

    private static void requireValidAmount(Payment payment, Money amount) {
        if (amount == null) {
            throw new BusinessRuleViolationException("INVALID_REFUND_AMOUNT", "Amount is required");
        }
        if (amount.getAmountUnits() <= 0) {
            throw new BusinessRuleViolationException("INVALID_REFUND_AMOUNT",
                    "Refund amount must be positive");
        }
        if (!amount.getCurrency().equals(payment.getAmount().getCurrency())) {
            throw new BusinessRuleViolationException("CURRENCY_MISMATCH",
                    "Refund currency must match payment currency");
        }
    }

    private static void requireRefundable(Payment payment) {
        PaymentStatus status = payment.getStatus();
        boolean refundable = status == PaymentStatus.CAPTURED
                || status == PaymentStatus.PARTIALLY_REFUNDED
                || status == PaymentStatus.SETTLED;
        if (!refundable) {
            throw new BusinessRuleViolationException("PAYMENT_NOT_REFUNDABLE",
                    "Payment cannot be refunded in status: " + status);
        }
    }

    private void publish(Refund refund, MerchantId merchantId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("refundId", refund.getId().toString());
        payload.put("paymentId", refund.getPayment().getId().toString());
        payload.put("merchantId", merchantId.value().toString());
        payload.put("amountUnits", refund.getAmount().getAmountUnits());
        payload.put("amountCurrency", refund.getAmount().getCurrency());
        payload.put("refundStatus", refund.getStatus().name());

        eventPublisher.publish(EventAggregateType.REFUND, refund.getId(),
                "REFUND_PROCESSED", payload);
    }

    private static RefundResponse toResponse(Refund refund) {
        return new RefundResponse(refund.getId(), refund.getPayment().getId(), refund.getAmount(),
                refund.getStatus(), refund.getReason(), refund.getBankReference(),
                refund.getCreatedAt(), refund.getProcessedAt());
    }
}
