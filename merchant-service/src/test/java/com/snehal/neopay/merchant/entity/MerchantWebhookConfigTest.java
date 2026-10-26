package com.snehal.neopay.merchant.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantWebhookConfigTest {

    private static MerchantWebhookConfig config(String eventTypes) {
        return MerchantWebhookConfig.builder()
                .targetUrl("https://example.com/hook")
                .eventTypes(eventTypes)
                .build();
    }

    @Test
    void nullSubscriptionListMeansAllEvents() {
        assertTrue(config(null).isSubscribedTo("payment.captured"));
    }

    @Test
    void blankSubscriptionListMeansAllEvents() {
        assertTrue(config("   ").isSubscribedTo("refund.created"));
    }

    @Test
    void allKeywordSubscribesToEverything() {
        assertTrue(config("ALL").isSubscribedTo("payout.processed"));
        assertTrue(config("all").isSubscribedTo("payout.processed"));
    }

    @Test
    void exactEventNameMatches() {
        assertTrue(config("payment.captured").isSubscribedTo("payment.captured"));
        assertFalse(config("payment.captured").isSubscribedTo("payment.failed"));
    }

    @Test
    void commaSeparatedListIsTrimmedAndCaseInsensitive() {
        MerchantWebhookConfig config = config(" payment.captured , Refund.Created ");

        assertTrue(config.isSubscribedTo("PAYMENT.CAPTURED"));
        assertTrue(config.isSubscribedTo("refund.created"));
        assertFalse(config.isSubscribedTo("payout.processed"));
    }

    @Test
    void enabledDefaultsToTrue() {
        assertTrue(config("ALL").getEnabled());
    }
}
