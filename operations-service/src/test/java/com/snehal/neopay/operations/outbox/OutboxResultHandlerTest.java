package com.snehal.neopay.operations.outbox;

import com.snehal.neopay.common.enums.EventAggregateType;
import com.snehal.neopay.common.enums.OutboxStatus;
import com.snehal.neopay.operations.entity.OutboxEvent;
import com.snehal.neopay.operations.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxResultHandlerTest {

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final OutboxResultHandler handler = new OutboxResultHandler(repository);

    @Test
    void publishedMarksStatusAndTimestamp() {
        OutboxEvent event = event(0);

        handler.handleEventPublished(event);

        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        verify(repository).save(event);
    }

    @Test
    void failureIncrementsAttemptsAndTruncatesError() {
        OutboxEvent event = event(0);

        handler.handleEventFailed(event, "x".repeat(1200));

        assertEquals(1, event.getAttempts());
        assertEquals(1000, event.getLastError().length());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
    }

    @Test
    void failureAtMaxAttemptsMarksFailed() {
        OutboxEvent event = event(2);

        handler.handleEventFailed(event, "boom");

        assertEquals(3, event.getAttempts());
        assertEquals(OutboxStatus.FAILED, event.getStatus());
    }

    private static OutboxEvent event(int attempts) {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType(EventAggregateType.PAYMENT)
                .aggregateId(UUID.randomUUID())
                .eventType("PAYMENT_CREATED")
                .payload(Map.of("merchantId", "m"))
                .status(OutboxStatus.PENDING)
                .attempts(attempts)
                .build();
    }
}
