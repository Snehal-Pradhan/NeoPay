package com.snehal.neopay.common.domain;

import java.util.UUID;

public record PaymentId(UUID value) {

    public PaymentId {
        if (value == null) {
            throw new IllegalArgumentException("PaymentId is required");
        }
    }

    public static PaymentId of(UUID value) {
        return new PaymentId(value);
    }
}
