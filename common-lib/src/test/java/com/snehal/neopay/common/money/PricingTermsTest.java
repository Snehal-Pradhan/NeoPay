package com.snehal.neopay.common.money;

import com.snehal.neopay.common.entity.Money;
import org.junit.jupiter.api.Test;

import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PricingTermsTest {

    private static PricingTerms terms(int feeBps, long fixed, long cap) {
        return new PricingTerms(feeBps, fixed, 1_800, 1_000, 500, cap, RoundingMode.HALF_UP);
    }

    @Test
    void standardTermsReproduceTheLegacySchedule() {
        PricingTerms standard = PricingTerms.standardIndia();
        Money gross = Money.inr(10_000);

        assertEquals(200, standard.fee(gross).getAmountUnits());
        assertEquals(36, standard.gst(standard.fee(gross)).getAmountUnits());
        assertEquals(20, standard.tds(standard.fee(gross)).getAmountUnits());
        assertEquals(500, standard.reserve(gross).getAmountUnits());
    }

    @Test
    void fixedComponentIsAddedToTheVariableFee() {
        assertEquals(205, terms(200, 5, 0).fee(Money.inr(10_000)).getAmountUnits());
    }

    @Test
    void capBoundsTheFee() {
        assertEquals(150, terms(200, 0, 150).fee(Money.inr(10_000)).getAmountUnits());
    }

    @Test
    void capDoesNotRaiseAFeeBelowIt() {
        assertEquals(100, terms(100, 0, 500).fee(Money.inr(10_000)).getAmountUnits());
    }

    @Test
    void zeroCapMeansUncapped() {
        assertEquals(1_000, terms(1_000, 0, 0).fee(Money.inr(10_000)).getAmountUnits());
    }

    @Test
    void gstAndTdsApplyToTheCappedFee() {
        Money fee = terms(200, 0, 150).fee(Money.inr(10_000));
        assertEquals(27, terms(200, 0, 150).gst(fee).getAmountUnits());
        assertEquals(15, terms(200, 0, 150).tds(fee).getAmountUnits());
    }
}
