package com.snehal.neopay.common.money;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxConverterTest {

    @Test
    void convertsUsdToInrAtLockedRate() {
        Money usd = Money.of(100, "USD");

        Money inr = FxConverter.convert(usd, new BigDecimal("83.00"), 0, Currency.INR);

        assertEquals(8_300, inr.getAmountUnits());
        assertEquals("INR", inr.getCurrency());
    }

    @Test
    void spreadIsApplied() {
        Money usd = Money.of(100, "USD");

        Money inr = FxConverter.convert(usd, new BigDecimal("83.00"), 100, Currency.INR);

        assertEquals(8_383, inr.getAmountUnits());
    }

    @Test
    void roundsHalfUpAtTargetScale() {
        Money usd = Money.of(1, "USD");

        Money inr = FxConverter.convert(usd, new BigDecimal("83.00"), 0, Currency.INR);

        assertEquals(83, inr.getAmountUnits());
    }

    @Test
    void inrToInrIsIdentity() {
        Money inr = Money.of(12_345, "INR");

        assertEquals(inr, FxConverter.convert(inr, BigDecimal.ONE, 0, Currency.INR));
    }

    @Test
    void roundTripStaysWithinOneMinorUnit() {
        Money original = Money.of(1_000_000, "USD");
        BigDecimal rate = new BigDecimal("83.00");

        Money inr = FxConverter.convert(original, rate, 0, Currency.INR);
        Money back = FxConverter.convert(inr, BigDecimal.ONE.divide(rate, 10, java.math.RoundingMode.HALF_UP),
                0, Currency.USD);

        assertTrue(Math.abs(back.getAmountUnits() - original.getAmountUnits()) <= 1,
                "round trip drift must stay within one minor unit");
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> FxConverter.convert(Money.of(1, "USD"), BigDecimal.ZERO, 0, Currency.INR));
        assertThrows(IllegalArgumentException.class,
                () -> FxConverter.convert(Money.of(1, "USD"), BigDecimal.ONE, -1, Currency.INR));
    }
}
