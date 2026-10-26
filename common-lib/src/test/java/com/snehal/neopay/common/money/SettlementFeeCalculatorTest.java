package com.snehal.neopay.common.money;

import com.snehal.neopay.common.entity.Money;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettlementFeeCalculatorTest {

    @Test
    void feeIsExactAtBinaryFloatingPointBoundaries() {
        // 0.02 is not representable in binary. double arithmetic undercharges
        // by one paisa for every amount congruent to 25 mod 100.
        assertFee(25, 1);
        assertFee(125, 3);
        assertFee(225, 5);
        assertFee(525, 11);
        assertFee(10_025, 201);
        assertFee(100_125, 2_003);
    }

    @Test
    void feeRoundsHalfUp() {
        assertFee(24, 0);   // 0.48 -> 0
        assertFee(25, 1);   // 0.50 -> 1
        assertFee(74, 1);   // 1.48 -> 1
        assertFee(75, 2);   // 1.50 -> 2
    }

    @Test
    void gstIsComputedOnRoundedFee() {
        assertEquals(0, SettlementFeeCalculator.gstFor(Money.inr(1)).getAmountUnits());
        assertEquals(2, SettlementFeeCalculator.gstFor(Money.inr(11)).getAmountUnits());
        assertEquals(36, SettlementFeeCalculator.gstFor(Money.inr(201)).getAmountUnits());
        assertEquals(361, SettlementFeeCalculator.gstFor(Money.inr(2_003)).getAmountUnits());
    }

    @Test
    void feePlusGstPlusNetEqualsGross() {
        for (int gross : new int[]{1, 25, 125, 525, 10_025, 100_125, 1_000_000}) {
            Money grossMoney = Money.inr(gross);
            Money fee = SettlementFeeCalculator.feeFor(grossMoney);
            Money gst = SettlementFeeCalculator.gstFor(fee);
            Money net = SettlementFeeCalculator.netFor(grossMoney, fee, gst);
            assertEquals(gross, fee.getAmountUnits() + gst.getAmountUnits() + net.getAmountUnits(),
                    "components must sum to gross for " + gross);
        }
    }

    private static void assertFee(int gross, int expectedFee) {
        assertEquals(expectedFee, SettlementFeeCalculator.feeFor(Money.inr(gross)).getAmountUnits(),
                "fee for gross " + gross);
    }
}
