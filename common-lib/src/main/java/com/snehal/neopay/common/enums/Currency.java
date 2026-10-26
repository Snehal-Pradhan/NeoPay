package com.snehal.neopay.common.enums;

import java.util.Locale;

/**
 * ISO 4217 currency with its minor-unit scale.
 */
public enum Currency {

    INR(2),
    USD(2),
    EUR(2),
    GBP(2),
    JPY(0),
    KWD(3);

    private final int scale;

    Currency(int scale) {
        this.scale = scale;
    }

    public int getScale() {
        return scale;
    }

    public static Currency from(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Currency is required");
        }
        try {
            return Currency.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("Unknown currency: " + code);
        }
    }
}
