package com.snehal.neopay.common.entity;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyPropertyTest {

    @Property(tries = 10_000)
    void addThenSubtractIsIdentity(
            @ForAll @IntRange(min = -1_000_000, max = 1_000_000) int base,
            @ForAll @IntRange(min = 0, max = 1_000_000) int delta) {
        Money a = Money.inr(base);
        Money b = Money.inr(delta);
        assertEquals(a, a.add(b).subtract(b));
    }

    @Property(tries = 10_000)
    void basisPointResultStaysBetweenFloorAndCeiling(
            @ForAll @IntRange(min = 0, max = 1_000_000_000) int amount,
            @ForAll @IntRange(min = 0, max = 10_000) int basisPoints) {
        long product = (long) amount * basisPoints;
        long floor = product / 10_000;
        long ceil = (product + 9_999) / 10_000;
        long result = Money.inr(amount)
                .multiplyByBasisPoints(basisPoints, RoundingMode.HALF_UP)
                .getAmountUnits();
        assertTrue(result >= floor, "below floor: " + result + " < " + floor);
        assertTrue(result <= ceil, "above ceiling: " + result + " > " + ceil);
    }

    @Property(tries = 5_000)
    void zeroRateProducesZero(
            @ForAll @IntRange(min = 0, max = 1_000_000_000) int amount) {
        assertEquals(0, Money.inr(amount)
                .multiplyByBasisPoints(0, RoundingMode.HALF_UP).getAmountUnits());
    }

    @Property(tries = 5_000)
    void fullRateIsIdentity(
            @ForAll @IntRange(min = 0, max = 1_000_000_000) int amount) {
        assertEquals(amount, Money.inr(amount)
                .multiplyByBasisPoints(10_000, RoundingMode.HALF_UP).getAmountUnits());
    }

    @Property(tries = 5_000)
    void additionIsCommutative(
            @ForAll @IntRange(min = -1_000_000, max = 1_000_000) int left,
            @ForAll @IntRange(min = -1_000_000, max = 1_000_000) int right) {
        assertEquals(Money.inr(left).add(Money.inr(right)),
                Money.inr(right).add(Money.inr(left)));
    }

    @Property(tries = 1_000)
    void currenciesCannotBeMixed(@ForAll @IntRange(min = 0, max = 1_000_000) int amount) {
        assertThrows(IllegalArgumentException.class,
                () -> Money.inr(amount).add(Money.of(amount, "USD")));
    }

    @Property(tries = 1_000)
    void majorUnitsRoundTripsThroughScale(
            @ForAll @IntRange(min = 0, max = 1_000_000_000) int amount) {
        Money money = Money.inr(amount);
        assertEquals(amount,
                money.majorUnits().movePointRight(money.scale()).longValueExact());
    }

    @Property(tries = 5_000)
    void basisPointMultiplicationIsMonotonic(
            @ForAll @IntRange(min = 0, max = 1_000_000_000) int lower,
            @ForAll @IntRange(min = 0, max = 1_000_000_000) int upper,
            @ForAll @IntRange(min = 0, max = 10_000) int basisPoints) {
        int small = Math.min(lower, upper);
        int large = Math.max(lower, upper);
        long smallResult = Money.inr(small)
                .multiplyByBasisPoints(basisPoints, RoundingMode.HALF_UP).getAmountUnits();
        long largeResult = Money.inr(large)
                .multiplyByBasisPoints(basisPoints, RoundingMode.HALF_UP).getAmountUnits();
        assertTrue(smallResult <= largeResult,
                "not monotonic: " + smallResult + " > " + largeResult);
    }
}
