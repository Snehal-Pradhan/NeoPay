package com.snehal.neopay.operations.webhook;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebhookMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final WebhookMetrics metrics = new WebhookMetrics(registry);

    @Test
    void deliveredAndFailedAreCountedSeparately() {
        metrics.delivered();
        metrics.delivered();
        metrics.failed("HTTP_500");

        assertEquals(2.0, registry.get("webhook_delivery_total").tag("status", "DELIVERED").counter().count());
        assertEquals(1.0, registry.get("webhook_delivery_total").tag("status", "HTTP_500").counter().count());
    }

    @Test
    void nullStatusIsTaggedAsFailed() {
        metrics.failed(null);

        assertEquals(1.0, registry.get("webhook_delivery_total").tag("status", "FAILED").counter().count());
    }
}
