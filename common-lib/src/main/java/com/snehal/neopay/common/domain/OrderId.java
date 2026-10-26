package com.snehal.neopay.common.domain;

import java.util.UUID;

public record OrderId(UUID value) {

    public OrderId {
        if (value == null) {
            throw new IllegalArgumentException("OrderId is required");
        }
    }

    public static OrderId of(UUID value) {
        return new OrderId(value);
    }
}
