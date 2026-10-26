package com.snehal.neopay.merchant.risk;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MerchantLimitUsageTest {

    private static MerchantLimitUsage usage(long amount, int requests) {
        return MerchantLimitUsage.builder()
                .merchantId(UUID.randomUUID())
                .usageDate(LocalDate.now())
                .amountUnits(amount)
                .requestCount(requests)
                .build();
    }

    @Test
    void addAccumulatesAmountAndCountsRequests() {
        MerchantLimitUsage usage = usage(1_000, 2);

        usage.add(250);
        usage.add(750);

        assertEquals(2_000, usage.getAmountUnits());
        assertEquals(4, usage.getRequestCount());
    }

    @Test
    void addDetectsOverflowInsteadOfSilentlyWrapping() {
        MerchantLimitUsage usage = usage(Long.MAX_VALUE, 0);

        assertThrows(ArithmeticException.class, () -> usage.add(1));
        assertEquals(Long.MAX_VALUE, usage.getAmountUnits());
        assertEquals(0, usage.getRequestCount());
    }
}
