package com.snehal.neopay.payment.service.impl;

import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.common.domain.PaymentId;
import com.snehal.neopay.common.enums.Currency;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.money.FxConverter;
import com.snehal.neopay.common.dto.PaymentSettlementView;
import com.snehal.neopay.common.enums.PaymentEvent;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.common.enums.RefundStatus;
import com.snehal.neopay.payment.api.PaymentLookupService;
import com.snehal.neopay.payment.entity.Payment;
import com.snehal.neopay.payment.fx.FxRateLock;
import com.snehal.neopay.payment.fx.FxRateLockService;
import com.snehal.neopay.payment.repository.PaymentRepository;
import com.snehal.neopay.payment.repository.RefundRepository;
import com.snehal.neopay.payment.statemachine.PaymentTransitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentLookupServiceImpl implements PaymentLookupService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PaymentTransitionService paymentTransitionService;
    private final FxRateLockService fxRateLockService;

    @Override
    @Transactional
    public List<PaymentSettlementView> findUnsettledCapturedPayments(MerchantId merchantId) {
        List<Payment> paymentList = paymentRepository.findByMerchantIdAndStatusInForUpdate(
                merchantId.value(),
                List.of(PaymentStatus.CAPTURED, PaymentStatus.PARTIALLY_REFUNDED));

        return paymentList.stream()
                .map(this::toSettlementView)
                .toList();
    }

    private PaymentSettlementView toSettlementView(Payment payment) {
        String currency = payment.getAmount().getCurrency();
        long grossUnits = payment.getAmount().getAmountUnits();
        long refundedUnits = refundRepository.sumAmountByPaymentAndStatus(
                payment.getId(), RefundStatus.PROCESSED);
        long netUnits = grossUnits - refundedUnits;

        if ("INR".equals(currency)) {
            return new PaymentSettlementView(payment.getId(), grossUnits, refundedUnits, currency,
                    payment.getCapturedAt(), netUnits, currency);
        }
        FxRateLock lock = fxRateLockService.require(payment.getId());
        var converted = FxConverter.convert(Money.of(netUnits, currency), lock.getRate(),
                lock.getSpreadBps(), Currency.INR);
        return new PaymentSettlementView(payment.getId(), grossUnits, refundedUnits, currency,
                payment.getCapturedAt(), converted.getAmountUnits(), converted.getCurrency());
    }

    @Override
    @Transactional
    public void markSettled(List<PaymentId> paymentList) {
        LocalDateTime now = LocalDateTime.now();
        List<Payment> payments = paymentRepository.findAllById(paymentList.stream().map(PaymentId::value).toList());
        for (Payment payment : payments) {
            paymentTransitionService.apply(payment, PaymentEvent.SETTLE);
            payment.markSettled(now);
        }
        paymentRepository.saveAll(payments);
    }
}













