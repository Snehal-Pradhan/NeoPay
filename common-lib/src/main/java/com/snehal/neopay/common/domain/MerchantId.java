package com.snehal.neopay.common.domain;

import java.util.UUID;

public record MerchantId(UUID value) {

    public MerchantId {
        if (value == null) {
            throw new IllegalArgumentException("MerchantId is required");
        }
    }

    public static MerchantId of(UUID value) {
        return new MerchantId(value);
    }
}
