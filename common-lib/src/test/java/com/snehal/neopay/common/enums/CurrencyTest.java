package com.snehal.neopay.common.enums;

import com.snehal.neopay.common.entity.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrencyTest {

    @Test
    void parsesCaseInsensitively() {
        assertEquals(Currency.INR, Currency.from("inr"));
        assertEquals(Currency.INR, Currency.from("  Inr "));
    }

    @Test
    void rejectsUnknownAndBlank() {
        assertThrows(IllegalArgumentException.class, () -> Currency.from("XYZ"));
        assertThrows(IllegalArgumentException.class, () -> Currency.from(null));
        assertThrows(IllegalArgumentException.class, () -> Currency.from(" "));
    }

    @Test
    void exposesMinorUnitScale() {
        assertEquals(2, Currency.INR.getScale());
        assertEquals(0, Currency.JPY.getScale());
        assertEquals(3, Currency.KWD.getScale());
    }

    @Test
    void moneyExposesScaleAndMajorUnits() {
        Money money = Money.inr(12_345);

        assertEquals(2, money.scale());
        assertEquals(new BigDecimal("123.45"), money.majorUnits());
    }
}
