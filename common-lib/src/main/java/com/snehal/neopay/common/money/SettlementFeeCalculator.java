package com.snehal.neopay.common.money;

import com.snehal.neopay.common.entity.Money;

import java.math.RoundingMode;

/**
 * Integer basis-point arithmetic for settlement fees.
 *
 * <p>Rates live in {@link FeeSchedule#standardIndia()}. Rounding is HALF_UP to the
 * minor unit. No floating point is used at any step.
 */
public final class SettlementFeeCalculator {

    static final FeeSchedule STANDARD = FeeSchedule.standardIndia();
    static final int FEE_BPS = STANDARD.feeBps();
    static final int GST_BPS = STANDARD.gstBps();

    private SettlementFeeCalculator() {
    }

    public static Money feeFor(Money gross) {
        return STANDARD.fee(gross);
    }

    public static Money gstFor(Money fee) {
        return STANDARD.gst(fee);
    }

    public static Money netFor(Money gross, Money fee, Money gst) {
        return STANDARD.net(gross, fee, gst);
    }

    public static Money netFor(Money gross) {
        Money fee = feeFor(gross);
        return netFor(gross, fee, gstFor(fee));
    }

    static Money withRounding(Money gross, int feeBps, int gstBps, RoundingMode rounding) {
        FeeSchedule schedule = new FeeSchedule(feeBps, gstBps, 0, 0, rounding);
        Money fee = schedule.fee(gross);
        return schedule.net(gross, fee, schedule.gst(fee));
    }
}
