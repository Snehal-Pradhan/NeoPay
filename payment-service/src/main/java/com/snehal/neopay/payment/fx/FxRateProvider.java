package com.snehal.neopay.payment.fx;

import com.snehal.neopay.common.enums.Currency;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Configured rate source. Replace with a provider client behind the same method.
 */
@Component
public class FxRateProvider {

    private final Map<Currency, BigDecimal> toInr;

    public FxRateProvider(
            @Value("${fx.rates.USD_INR:83.00}") String usd,
            @Value("${fx.rates.EUR_INR:90.00}") String eur,
            @Value("${fx.rates.GBP_INR:105.00}") String gbp) {
        this.toInr = Map.of(
                Currency.USD, new BigDecimal(usd),
                Currency.EUR, new BigDecimal(eur),
                Currency.GBP, new BigDecimal(gbp));
    }

    public BigDecimal rate(Currency base, Currency quote) {
        if (quote != Currency.INR) {
            throw new IllegalArgumentException("Only INR quotes are supported");
        }
        if (base == Currency.INR) {
            return BigDecimal.ONE;
        }
        BigDecimal rate = toInr.get(base);
        if (rate == null) {
            throw new IllegalArgumentException("No configured rate for " + base);
        }
        return rate;
    }
}
