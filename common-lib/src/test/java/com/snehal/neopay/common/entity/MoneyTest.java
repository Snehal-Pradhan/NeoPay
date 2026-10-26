package com.snehal.neopay.common.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {

    @Test
    void normalizesCurrencyToUpperCase() {
        assertEquals("INR", Money.of(100, "inr").getCurrency());
        assertEquals("INR", Money.of(100, "  Inr  ").getCurrency());
    }

    @Test
    void rejectsNullAndBlankCurrency() {
        assertThrows(IllegalArgumentException.class, () -> Money.of(100, null));
        assertThrows(IllegalArgumentException.class, () -> Money.of(100, "  "));
    }

    @Test
    void rejectsUnknownCurrency() {
        assertThrows(IllegalArgumentException.class, () -> Money.of(100, "XYZ"));
    }

    @Test
    void acceptsSupportedForeignCurrency() {
        assertEquals("USD", Money.of(100, "usd").getCurrency());
    }

    @Test
    void addDetectsOverflow() {
        Money max = Money.inr(Long.MAX_VALUE);
        Money one = Money.inr(1);
        assertThrows(ArithmeticException.class, () -> max.add(one));
    }

    @Test
    void subtractDetectsOverflow() {
        Money min = Money.inr(Long.MIN_VALUE);
        Money one = Money.inr(1);
        assertThrows(ArithmeticException.class, () -> min.subtract(one));
    }

    @Test
    void addAndSubtractAreExact() {
        Money a = Money.inr(1_000);
        Money b = Money.inr(250);
        assertEquals(1_250, a.add(b).getAmountUnits());
        assertEquals(750, a.subtract(b).getAmountUnits());
        assertEquals(a, a.add(b).subtract(b));
    }

    @Test
    void rejectsNullOperand() {
        assertThrows(IllegalArgumentException.class, () -> Money.inr(100).add(null));
        assertThrows(IllegalArgumentException.class, () -> Money.inr(100).subtract(null));
    }

    @Test
    void basisPointsUseExactDecimalRounding() {
        // 25 paise * 200 bps = 0.50 paise -> 1 under HALF_UP
        assertEquals(1, Money.inr(25).multiplyByBasisPoints(200, RoundingMode.HALF_UP).getAmountUnits());
        // 100125 paise * 200 bps = 2002.50 -> 2003
        assertEquals(2_003, Money.inr(100_125).multiplyByBasisPoints(200, RoundingMode.HALF_UP).getAmountUnits());
        // 24 paise * 200 bps = 0.48 -> 0
        assertEquals(0, Money.inr(24).multiplyByBasisPoints(200, RoundingMode.HALF_UP).getAmountUnits());
        // 75 paise * 200 bps = 1.50 -> 2 HALF_UP, 2 HALF_EVEN (even)
        assertEquals(2, Money.inr(75).multiplyByBasisPoints(200, RoundingMode.HALF_UP).getAmountUnits());
    }

    @Test
    void basisPointsRejectNegativeInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> Money.inr(100).multiplyByBasisPoints(-1, RoundingMode.HALF_UP));
        assertThrows(IllegalArgumentException.class,
                () -> Money.inr(-100).multiplyByBasisPoints(200, RoundingMode.HALF_UP));
        assertThrows(IllegalArgumentException.class,
                () -> Money.inr(100).multiplyByBasisPoints(200, null));
    }

    @Test
    void basisPointsDetectResultOverflow() {
        assertThrows(ArithmeticException.class,
                () -> Money.inr(Long.MAX_VALUE).multiplyByBasisPoints(20_000, RoundingMode.HALF_UP));
    }

    @Test
    void scaleReflectsCurrencyMinorUnits() {
        assertEquals(2, Money.inr(100).scale());
        assertEquals(0, Money.of(100, "JPY").scale());
        assertEquals(3, Money.of(100, "KWD").scale());
    }

    @Test
    void majorUnitsUsesCurrencyScale() {
        assertEquals(new BigDecimal("1.50"), Money.inr(150).majorUnits());
        assertEquals(new BigDecimal("100"), Money.of(100, "JPY").majorUnits());
    }

    @Test
    void comparisonIsStrictAndCurrencyChecked() {
        assertTrue(Money.inr(101).isGreaterThan(Money.inr(100)));
        assertFalse(Money.inr(100).isGreaterThan(Money.inr(100)));
        assertThrows(IllegalArgumentException.class,
                () -> Money.inr(100).isGreaterThan(Money.of(100, "USD")));
        assertThrows(IllegalArgumentException.class,
                () -> Money.inr(100).isGreaterThan(null));
    }

    @Test
    void zeroBasisPointsAndZeroAmountAreAllowed() {
        assertEquals(0, Money.inr(500).multiplyByBasisPoints(0, RoundingMode.HALF_UP).getAmountUnits());
        assertEquals(0, Money.inr(0).multiplyByBasisPoints(200, RoundingMode.HALF_UP).getAmountUnits());
    }
}
