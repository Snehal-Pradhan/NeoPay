package com.snehal.neopay.payment.outbox;

import com.snehal.neopay.common.config.KafkaProperties;
import com.snehal.neopay.common.enums.EventAggregateType;
import com.snehal.neopay.common.enums.OutboxStatus;
import com.snehal.neopay.payment.entity.OutboxEvent;
import com.snehal.neopay.payment.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPollerTest {

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final KafkaProperties kafkaProperties = mock(KafkaProperties.class);
    private final OutboxResultHandler handler = mock(OutboxResultHandler.class);
    private final OutboxPoller poller =
            new OutboxPoller(repository, kafkaTemplate, kafkaProperties, handler);

    private static OutboxEvent event(Map<String, Object> payload) {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType(EventAggregateType.PAYMENT)
                .aggregateId(UUID.randomUUID())
                .eventType("PAYMENT_CREATED")
                .payload(payload)
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .build();
    }

    @Test
    void publishesPendingEventsAndMarksThemPublished() {
        OutboxEvent pending = event(Map.of("merchantId", "merchant-1"));
        when(repository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(pending));
        when(kafkaProperties.topicFor(EventAggregateType.PAYMENT)).thenReturn("payments.events");
        doReturn(CompletableFuture.completedFuture(null))
                .when(kafkaTemplate).send(anyString(), anyString(), any());

        poller.poll();

        verify(kafkaTemplate).send(eq("payments.events"), eq("merchant-1"), any());
        verify(handler).handleEventPublished(pending);
        verify(handler, never()).handleEventFailed(any(), anyString());
    }

    @Test
    void missingMerchantIdUsesTheUnknownKey() {
        OutboxEvent pending = event(Map.of("amountUnits", 100));
        when(repository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(pending));
        when(kafkaProperties.topicFor(EventAggregateType.PAYMENT)).thenReturn("payments.events");
        doReturn(CompletableFuture.completedFuture(null))
                .when(kafkaTemplate).send(anyString(), anyString(), any());

        poller.poll();

        verify(kafkaTemplate).send(eq("payments.events"), eq("unknown"), any());
    }

    @Test
    void brokerFailureIsHandedToTheResultHandler() {
        OutboxEvent pending = event(Map.of("merchantId", "merchant-1"));
        when(repository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(pending));
        when(kafkaProperties.topicFor(EventAggregateType.PAYMENT)).thenReturn("payments.events");
        doReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")))
                .when(kafkaTemplate).send(anyString(), anyString(), any());

        poller.poll();

        verify(handler).handleEventFailed(eq(pending), anyString());
        verify(handler, never()).handleEventPublished(any());
    }

    @Test
    void emptyOutboxDoesNothing() {
        when(repository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)).thenReturn(List.of());

        poller.poll();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        verify(handler, never()).handleEventPublished(any());
    }
}
