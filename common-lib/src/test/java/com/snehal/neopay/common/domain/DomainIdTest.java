package com.snehal.neopay.common.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomainIdTest {

    @Test
    void idsRejectNull() {
        assertThrows(IllegalArgumentException.class, () -> MerchantId.of(null));
        assertThrows(IllegalArgumentException.class, () -> PaymentId.of(null));
        assertThrows(IllegalArgumentException.class, () -> OrderId.of(null));
    }

    @Test
    void idsWrapAndExposeTheValue() {
        UUID value = UUID.randomUUID();

        assertEquals(value, MerchantId.of(value).value());
        assertEquals(value, PaymentId.of(value).value());
        assertEquals(value, OrderId.of(value).value());
    }

    @Test
    void idsCompareByValue() {
        UUID value = UUID.randomUUID();

        assertEquals(MerchantId.of(value), MerchantId.of(value));
        assertEquals(PaymentId.of(value), PaymentId.of(value));
        assertEquals(OrderId.of(value), OrderId.of(value));
        assertNotEquals(MerchantId.of(value), MerchantId.of(UUID.randomUUID()));
    }
}
