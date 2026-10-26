package com.snehal.neopay.common.audit;

import com.snehal.neopay.common.context.MerchantContext;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditorAwareImplTest {

    @Test
    void keyIdTakesPrecedence() {
        MerchantContext context = new MerchantContext();
        context.setKeyId("np_test_key");
        context.setMerchantId(UUID.randomUUID());

        assertEquals(Optional.of("np_test_key"),
                new AuditorAwareImpl(context).getCurrentAuditor());
    }

    @Test
    void merchantIdUsedWhenNoKey() {
        MerchantContext context = new MerchantContext();
        UUID merchantId = UUID.randomUUID();
        context.setMerchantId(merchantId);

        assertEquals(Optional.of("merchant_id: " + merchantId),
                new AuditorAwareImpl(context).getCurrentAuditor());
    }

    @Test
    void fallsBackToSystem() {
        assertEquals(Optional.of("SYSTEM"),
                new AuditorAwareImpl(new MerchantContext()).getCurrentAuditor());
    }
}
