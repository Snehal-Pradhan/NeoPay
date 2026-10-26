package com.snehal.neopay.common.money;

import com.snehal.neopay.common.entity.Money;
import org.junit.jupiter.api.Test;

import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeeScheduleTest {

    @Test
    void standardScheduleUsesTwoPercentAndEighteenPercentGst() {
        FeeSchedule schedule = FeeSchedule.standardIndia();

        assertEquals(200, schedule.feeBps());
        assertEquals(1_800, schedule.gstBps());
        assertEquals(RoundingMode.HALF_UP, schedule.rounding());
        assertEquals(1_000, schedule.tdsBps());
        assertEquals(500, schedule.reserveBps());
    }

    @Test
    void customScheduleAppliesItsOwnRates() {
        FeeSchedule schedule = new FeeSchedule(100, 1_800, 0, 0, RoundingMode.HALF_UP);

        Money gross = Money.inr(10_000);
        Money fee = schedule.fee(gross);

        assertEquals(100, fee.getAmountUnits());
        assertEquals(18, schedule.gst(fee).getAmountUnits());
        assertEquals(9_882, schedule.net(gross, fee, schedule.gst(fee)).getAmountUnits());
    }
}
