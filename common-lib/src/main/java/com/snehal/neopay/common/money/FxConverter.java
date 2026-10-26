package com.snehal.neopay.common.money;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.Currency;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts between currencies at a locked rate plus a spread expressed in basis points.
 * The result is rounded HALF_UP at the target currency scale.
 */
public final class FxConverter {

    private static final int BPS_DENOMINATOR = 10_000;

    private FxConverter() {
    }

    public static Money convert(Money source, BigDecimal rate, int spreadBps, Currency target) {
        if (source == null || rate == null || target == null) {
            throw new IllegalArgumentException("Source, rate and target are required");
        }
        if (rate.signum() <= 0) {
            throw new IllegalArgumentException("Rate must be positive");
        }
        if (spreadBps < 0) {
            throw new IllegalArgumentException("Spread must be non-negative");
        }

        BigDecimal targetMajor = BigDecimal.valueOf(source.getAmountUnits())
                .movePointLeft(source.scale())
                .multiply(rate)
                .multiply(BigDecimal.valueOf(BPS_DENOMINATOR + spreadBps))
                .movePointLeft(4)
                .setScale(target.getScale(), RoundingMode.HALF_UP);

        long units = targetMajor.movePointRight(target.getScale()).longValueExact();
        return Money.of(units, target.name());
    }
}
