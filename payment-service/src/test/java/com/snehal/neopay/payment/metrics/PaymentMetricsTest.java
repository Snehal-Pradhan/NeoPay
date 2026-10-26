package com.snehal.neopay.payment.metrics;

import com.snehal.neopay.common.enums.OutboxStatus;
import com.snehal.neopay.payment.repository.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final PaymentMetrics metrics = new PaymentMetrics(registry, repository);

    @Test
    void countersCarryBoundedTags() {
        metrics.paymentInitiated("UPI");
        metrics.paymentInitiated("UPI");
        metrics.paymentCaptured("CARD");
        metrics.paymentFailed("CARD_DECLINED");
        metrics.idempotencyReplay();

        assertEquals(2.0, registry.get("payment_initiated_total").tag("method", "UPI").counter().count());
        assertEquals(1.0, registry.get("payment_captured_total").tag("method", "CARD").counter().count());
        assertEquals(1.0, registry.get("payment_failed_total")
                .tag("reason", "CARD_DECLINED").counter().count());
        assertEquals(1.0, registry.get("payment_idempotency_replay_total").counter().count());
    }

    @Test
    void nullReasonIsNormalised() {
        metrics.paymentFailed(null);

        assertEquals(1.0, registry.get("payment_failed_total")
                .tag("reason", "UNKNOWN").counter().count());
    }

    @Test
    void gaugesReportZeroWhenOutboxIsEmpty() {
        when(repository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(java.util.List.of());
        when(repository.findFirstByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(Optional.empty());

        metrics.registerGauges();

        assertEquals(0.0, registry.get("outbox_pending_count").gauge().value());
        assertEquals(0.0, registry.get("outbox_lag_seconds").gauge().value());
    }
}
