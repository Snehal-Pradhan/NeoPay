package com.snehal.neopay.payment.metrics;

import com.snehal.neopay.common.enums.OutboxStatus;
import com.snehal.neopay.payment.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Business metrics for the payment flow.
 *
 * <p>Cardinality is bounded: method has four values, reason is a fixed error code.
 * Endpoint latency is already recorded by the Micrometer HTTP server instrumentation,
 * so no per-endpoint timer is declared here.
 */
@Component
@RequiredArgsConstructor
public class PaymentMetrics {

    private final MeterRegistry registry;
    private final OutboxEventRepository outboxEventRepository;

    @PostConstruct
    void registerGauges() {
        Gauge.builder("outbox_pending_count", outboxEventRepository,
                        repo -> repo.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING).size())
                .description("Outbox events awaiting publication")
                .register(registry);

        Gauge.builder("outbox_lag_seconds", outboxEventRepository, PaymentMetrics::lagSeconds)
                .description("Age in seconds of the oldest unpublished outbox event")
                .register(registry);
    }

    public void paymentInitiated(String method) {
        counter("payment_initiated_total", "method", method).increment();
    }

    public void paymentCaptured(String method) {
        counter("payment_captured_total", "method", method).increment();
    }

    public void paymentFailed(String reason) {
        counter("payment_failed_total", "reason", sanitize(reason)).increment();
    }

    public void refundProcessed() {
        counter("refund_processed_total").increment();
    }

    public void refundReplayed() {
        counter("refund_idempotency_replay_total").increment();
    }

    public void idempotencyReplay() {
        counter("payment_idempotency_replay_total").increment();
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }

    private static double lagSeconds(OutboxEventRepository repo) {
        return repo.findFirstByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)
                .map(event -> event.getCreatedAt() == null
                        ? 0.0
                        : Duration.between(event.getCreatedAt(), LocalDateTime.now()).toMillis() / 1000.0)
                .orElse(0.0);
    }

    private static String sanitize(String reason) {
        return reason == null || reason.isBlank() ? "UNKNOWN" : reason;
    }
}
