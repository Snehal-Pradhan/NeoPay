package com.snehal.neopay.common.entity;

import com.snehal.neopay.common.enums.Currency;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.Set;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
public class Money {

    private static final int BASIS_POINT_DENOMINATOR = 10_000;
    private static final Set<Currency> SUPPORTED = EnumSet.allOf(Currency.class);

    private long amountUnits;
    private String currency;

    public static Money of(long amountUnits, String currency) {
        return new Money(amountUnits, normalizeCurrency(currency));
    }

    public static Money inr(long amountUnits) {
        return new Money(amountUnits, Currency.INR.name());
    }

    /**
     * Minor-unit scale of the currency. INR is 2, JPY is 0, KWD is 3.
     */
    public int scale() {
        return Currency.valueOf(currency).getScale();
    }

    /**
     * Amount expressed in major units at the currency scale. Display only; never
     * use for arithmetic.
     */
    public BigDecimal majorUnits() {
        return BigDecimal.valueOf(amountUnits).movePointLeft(scale());
    }

    public Money add(Money other) {
        requireSameCurrency(other, "add");
        return new Money(Math.addExact(this.amountUnits, other.amountUnits), this.currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other, "subtract");
        return new Money(Math.subtractExact(this.amountUnits, other.amountUnits), this.currency);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other, "compare");
        return this.amountUnits > other.amountUnits;
    }

    public Money multiplyByBasisPoints(int basisPoints, RoundingMode roundingMode) {
        if (basisPoints < 0) {
            throw new IllegalArgumentException("Basis points must be non-negative: " + basisPoints);
        }
        if (roundingMode == null) {
            throw new IllegalArgumentException("RoundingMode is required");
        }
        if (amountUnits < 0) {
            throw new IllegalArgumentException("Amount must be non-negative: " + amountUnits);
        }
        long product = Math.multiplyExact(amountUnits, (long) basisPoints);
        BigDecimal result = BigDecimal.valueOf(product)
                .divide(BigDecimal.valueOf(BASIS_POINT_DENOMINATOR), 0, roundingMode);
        return new Money(result.longValueExact(), this.currency);
    }

    private void requireSameCurrency(Money other, String operation) {
        if (other == null) {
            throw new IllegalArgumentException("Cannot " + operation + " null Money");
        }
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot " + operation + " Money with different currencies: "
                            + this.currency + " and " + other.currency);
        }
    }

    private static String normalizeCurrency(String currency) {
        Currency parsed = Currency.from(currency);
        if (!SUPPORTED.contains(parsed)) {
            throw new IllegalArgumentException("Unsupported currency: " + currency);
        }
        return parsed.name();
    }
}
