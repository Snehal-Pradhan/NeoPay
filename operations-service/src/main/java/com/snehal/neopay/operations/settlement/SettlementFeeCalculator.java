package com.snehal.neopay.operations.settlement;

import com.snehal.neopay.common.dto.PaymentSettlementView;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.money.PricingTerms;
import com.snehal.neopay.operations.entity.MerchantFeeSchedule;
import com.snehal.neopay.operations.entity.MerchantMethodPricing;
import com.snehal.neopay.operations.repository.MerchantFeeScheduleRepository;
import com.snehal.neopay.operations.repository.MerchantMethodPricingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves effective pricing and aggregates deductions.
 *
 * <p>Fees are computed per payment method so a method-specific override applies only to
 * that method's volume. With no overrides the aggregation collapses to a single group and
 * reproduces the previous single-schedule arithmetic exactly.
 */
@Service
@RequiredArgsConstructor
public class SettlementFeeCalculator {

    private final MerchantFeeScheduleRepository defaults;
    private final MerchantMethodPricingRepository overrides;

    public MerchantPricing pricingFor(UUID merchantId, LocalDate onDate) {
        PricingTerms base = defaults.findById(merchantId)
                .map(SettlementFeeCalculator::toTerms)
                .orElseGet(PricingTerms::standardIndia);

        Map<String, PricingTerms> byMethod = new LinkedHashMap<>();
        for (MerchantMethodPricing row : overrides.findApplicable(merchantId, onDate)) {
            byMethod.putIfAbsent(row.getPaymentMethod(), toTerms(row));
        }
        return new MerchantPricing(base, Map.copyOf(byMethod));
    }

    public FeeBreakdown breakdown(MerchantPricing pricing, String currency,
                                  List<PaymentSettlementView> payments) {
        Map<String, Long> capturedByMethod = new LinkedHashMap<>();
        Map<String, Long> refundedByMethod = new LinkedHashMap<>();
        for (PaymentSettlementView payment : payments) {
            String method = payment.paymentMethod() == null ? "UNKNOWN" : payment.paymentMethod();
            capturedByMethod.merge(method, payment.settlementAmountUnits(), Long::sum);
            refundedByMethod.merge(method, payment.refundedAmountUnits(), Long::sum);
        }

        long feeUnits = 0;
        long gstUnits = 0;
        long tdsUnits = 0;
        for (Map.Entry<String, Long> group : capturedByMethod.entrySet()) {
            PricingTerms terms = pricing.termsFor(group.getKey());
            long gross = group.getValue();
            long netBase = gross - refundedByMethod.getOrDefault(group.getKey(), 0L);
            if (gross <= 0 || netBase <= 0) {
                continue;
            }
            Money groupGross = Money.of(gross, currency);
            Money fee = terms.fee(groupGross);
            Money gst = terms.gst(fee);
            Money tds = terms.tds(fee);
            feeUnits += fee.getAmountUnits();
            gstUnits += gst.getAmountUnits();
            tdsUnits += tds.getAmountUnits();
        }
        return new FeeBreakdown(Money.of(feeUnits, currency), Money.of(gstUnits, currency),
                Money.of(tdsUnits, currency));
    }

    private static PricingTerms toTerms(MerchantFeeSchedule stored) {
        return new PricingTerms(stored.getFeeBps(), 0, stored.getGstBps(),
                stored.getTdsBps(), stored.getReserveBps(), 0, RoundingMode.HALF_UP);
    }

    private static PricingTerms toTerms(MerchantMethodPricing stored) {
        return new PricingTerms(stored.getFeeBps(), stored.getFixedFeeUnits(),
                stored.getGstBps(), stored.getTdsBps(), stored.getReserveBps(),
                stored.getMaxFeeUnits(), RoundingMode.HALF_UP);
    }
}
