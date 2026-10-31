package com.snehal.neopay.common.money;

import com.snehal.neopay.common.entity.Money;

import java.math.RoundingMode;

/**
 * Effective merchant pricing terms: a basis-point rate, an optional fixed component and an
 * optional cap. GST and TDS are charged on the fee, reserve on the settled base.
 *
 * @param maxFeeUnits cap in minor units; {@code 0} means uncapped
 */
public record PricingTerms(int feeBps, long fixedFeeUnits, int gstBps, int tdsBps,
                           int reserveBps, long maxFeeUnits, RoundingMode rounding) {

    public static PricingTerms standardIndia() {
        return new PricingTerms(200, 0, 1_800, 1_000, 500, 0, RoundingMode.HALF_UP);
    }

    public Money fee(Money gross) {
        Money variable = gross.multiplyByBasisPoints(feeBps, rounding);
        Money total = fixedFeeUnits == 0
                ? variable
                : variable.add(Money.of(fixedFeeUnits, gross.getCurrency()));
        if (maxFeeUnits > 0 && total.isGreaterThan(Money.of(maxFeeUnits, gross.getCurrency()))) {
            return Money.of(maxFeeUnits, gross.getCurrency());
        }
        return total;
    }

    public Money gst(Money fee) {
        return fee.multiplyByBasisPoints(gstBps, rounding);
    }

    public Money tds(Money fee) {
        return fee.multiplyByBasisPoints(tdsBps, rounding);
    }

    public Money reserve(Money base) {
        return base.multiplyByBasisPoints(reserveBps, rounding);
    }
}
