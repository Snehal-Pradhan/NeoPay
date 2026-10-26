package com.snehal.neopay.common.money;

import com.snehal.neopay.common.entity.Money;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementFeePropertyTest {

    @Property(tries = 10_000)
    void componentsAlwaysSumToGross(
            @ForAll @IntRange(min = 0, max = 100_000_000) int grossUnits) {
        Money gross = Money.inr(grossUnits);
        Money fee = SettlementFeeCalculator.feeFor(gross);
        Money gst = SettlementFeeCalculator.gstFor(fee);
        Money net = SettlementFeeCalculator.netFor(gross, fee, gst);

        assertEquals(grossUnits,
                fee.getAmountUnits() + gst.getAmountUnits() + net.getAmountUnits(),
                "fee + gst + net must equal gross for " + grossUnits);
    }

    @Property(tries = 10_000)
    void componentsAreNonNegative(
            @ForAll @IntRange(min = 0, max = 100_000_000) int grossUnits) {
        Money gross = Money.inr(grossUnits);
        Money fee = SettlementFeeCalculator.feeFor(gross);
        Money gst = SettlementFeeCalculator.gstFor(fee);
        Money net = SettlementFeeCalculator.netFor(gross, fee, gst);

        assertTrue(fee.getAmountUnits() >= 0);
        assertTrue(gst.getAmountUnits() >= 0);
        assertTrue(net.getAmountUnits() >= 0);
    }

    @Property(tries = 5_000)
    void feeIsMonotonic(
            @ForAll @IntRange(min = 0, max = 50_000_000) int smaller,
            @ForAll @IntRange(min = 0, max = 50_000_000) int larger) {
        int low = Math.min(smaller, larger);
        int high = Math.max(smaller, larger);

        long feeLow = SettlementFeeCalculator.feeFor(Money.inr(low)).getAmountUnits();
        long feeHigh = SettlementFeeCalculator.feeFor(Money.inr(high)).getAmountUnits();

        assertTrue(feeLow <= feeHigh,
                "fee must not decrease: " + low + " -> " + feeLow + ", " + high + " -> " + feeHigh);
    }

    @Property(tries = 5_000)
    void feeNeverExceedsGross(
            @ForAll @IntRange(min = 0, max = 100_000_000) int grossUnits) {
        long fee = SettlementFeeCalculator.feeFor(Money.inr(grossUnits)).getAmountUnits();
        assertTrue(fee <= grossUnits, "fee " + fee + " exceeds gross " + grossUnits);
    }
}
