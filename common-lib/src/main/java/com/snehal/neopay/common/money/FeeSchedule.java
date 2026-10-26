package com.snehal.neopay.common.money;

import com.snehal.neopay.common.entity.Money;

import java.math.RoundingMode;

/**
 * Fee policy. Rates are basis points; rounding is explicit.
 *
 * <p>{@code tdsBps} is charged on the fee, {@code reserveBps} on the settled base.
 */
public record FeeSchedule(int feeBps, int gstBps, int tdsBps, int reserveBps, RoundingMode rounding) {

    public static FeeSchedule standardIndia() {
        return new FeeSchedule(200, 1_800, 1_000, 500, RoundingMode.HALF_UP);
    }

    public Money fee(Money gross) {
        return gross.multiplyByBasisPoints(feeBps, rounding);
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

    public Money net(Money gross, Money fee, Money gst) {
        return gross.subtract(fee).subtract(gst);
    }
}
