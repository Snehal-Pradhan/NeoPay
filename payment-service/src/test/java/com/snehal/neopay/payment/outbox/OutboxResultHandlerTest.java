package com.snehal.neopay.payment.outbox;

import com.snehal.neopay.common.enums.OutboxStatus;
import com.snehal.neopay.payment.entity.OutboxEvent;
import com.snehal.neopay.payment.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxResultHandlerTest {

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final OutboxResultHandler handler = new OutboxResultHandler(repository);

    @Test
    void thirdFailureMarksTheEventFailed() {
        OutboxEvent event = OutboxEvent.builder().attempts(2).status(OutboxStatus.PENDING).build();

        handler.handleEventFailed(event, "kafka down");

        assertEquals(3, event.getAttempts());
        assertEquals(OutboxStatus.FAILED, event.getStatus());
        assertEquals("kafka down", event.getLastError());
        verify(repository).save(event);
    }

    @Test
    void firstFailureKeepsTheEventPending() {
        OutboxEvent event = OutboxEvent.builder().attempts(0).status(OutboxStatus.PENDING).build();

        handler.handleEventFailed(event, "timeout");

        assertEquals(1, event.getAttempts());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
    }

    @Test
    void blankErrorBecomesUnknown() {
        OutboxEvent event = OutboxEvent.builder().attempts(0).status(OutboxStatus.PENDING).build();

        handler.handleEventFailed(event, "  ");

        assertEquals("unknown error", event.getLastError());
    }

    @Test
    void longErrorIsTruncated() {
        OutboxEvent event = OutboxEvent.builder().attempts(0).status(OutboxStatus.PENDING).build();

        handler.handleEventFailed(event, "x".repeat(1_500));

        assertEquals(1_000, event.getLastError().length());
    }

    @Test
    void publishedEventIsStampedAndSaved() {
        OutboxEvent event = OutboxEvent.builder().status(OutboxStatus.PENDING).build();

        handler.handleEventPublished(event);

        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        verify(repository).save(event);
    }
}
