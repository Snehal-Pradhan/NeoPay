package com.snehal.neopay.payment.fx;

import com.snehal.neopay.common.enums.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FxRateProviderTest {

    private final FxRateProvider provider = new FxRateProvider("83.25", "90.50", "105.75");

    @Test
    void configuredRatesAreReturned() {
        assertEquals(new BigDecimal("83.25"), provider.rate(Currency.USD, Currency.INR));
        assertEquals(new BigDecimal("90.50"), provider.rate(Currency.EUR, Currency.INR));
        assertEquals(new BigDecimal("105.75"), provider.rate(Currency.GBP, Currency.INR));
    }

    @Test
    void inrToInrIsOne() {
        assertEquals(BigDecimal.ONE, provider.rate(Currency.INR, Currency.INR));
    }

    @Test
    void nonInrQuoteIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.rate(Currency.USD, Currency.EUR));
    }

    @Test
    void unconfiguredBaseIsRejected() {
        FxRateProvider partial = new FxRateProvider("83", "90", "105");
        assertThrows(IllegalArgumentException.class, () -> partial.rate(Currency.JPY, Currency.INR));
    }
}
