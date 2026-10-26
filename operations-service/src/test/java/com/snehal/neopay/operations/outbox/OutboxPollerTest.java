package com.snehal.neopay.operations.outbox;

import com.snehal.neopay.common.config.KafkaProperties;
import com.snehal.neopay.common.enums.EventAggregateType;
import com.snehal.neopay.common.enums.OutboxStatus;
import com.snehal.neopay.operations.entity.OutboxEvent;
import com.snehal.neopay.operations.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPollerTest {

    @Test
    void pendingEventsArePublishedAndMarked() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        KafkaProperties properties = mock(KafkaProperties.class);
        OutboxResultHandler handler = mock(OutboxResultHandler.class);
        OutboxPoller poller = new OutboxPoller(repository, kafka, properties, handler);

        OutboxEvent event = event();
        when(repository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(event));
        when(properties.topicFor(EventAggregateType.SETTLEMENT)).thenReturn("settlement.events");
        when(kafka.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        poller.poll();

        verify(kafka).send(eq("settlement.events"), eq("merchant-1"), any());
        verify(handler).handleEventPublished(event);
    }

    @Test
    void publishFailureIsRecorded() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        KafkaProperties properties = mock(KafkaProperties.class);
        OutboxResultHandler handler = mock(OutboxResultHandler.class);
        OutboxPoller poller = new OutboxPoller(repository, kafka, properties, handler);

        OutboxEvent event = event();
        when(repository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(event));
        when(properties.topicFor(EventAggregateType.SETTLEMENT)).thenReturn("settlement.events");
        when(kafka.send(anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("broker down"));

        poller.poll();

        verify(handler).handleEventFailed(eq(event), anyString());
    }

    @Test
    void emptyOutboxDoesNothing() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        KafkaProperties properties = mock(KafkaProperties.class);
        OutboxResultHandler handler = mock(OutboxResultHandler.class);
        OutboxPoller poller = new OutboxPoller(repository, kafka, properties, handler);
        when(repository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)).thenReturn(List.of());

        poller.poll();

        verify(handler, org.mockito.Mockito.never()).handleEventPublished(any());
    }

    private static OutboxEvent event() {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType(EventAggregateType.SETTLEMENT)
                .aggregateId(UUID.randomUUID())
                .eventType("SETTLEMENT_PROCESSED")
                .payload(Map.of("merchantId", "merchant-1"))
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .build();
    }
}
