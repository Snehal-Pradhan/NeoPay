package com.snehal.neopay.operations.webhook;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebhookMetrics {

    private final MeterRegistry registry;

    public void delivered() {
        counter("DELIVERED").increment();
    }

    public void failed(String status) {
        counter(status == null ? "FAILED" : status).increment();
    }

    private Counter counter(String status) {
        return Counter.builder("webhook_delivery_total")
                .tag("status", status)
                .register(registry);
    }
}
