package com.snehal.neopay.operations.outbox;

import com.snehal.neopay.common.enums.EventAggregateType;
import com.snehal.neopay.operations.entity.OutboxEvent;
import com.snehal.neopay.operations.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxEventPublisherTest {

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final OutboxEventPublisher publisher = new OutboxEventPublisher(repository);

    @Test
    void publishStoresTheEventWithItsAggregateAndPayload() {
        UUID aggregateId = UUID.randomUUID();
        Map<String, Object> payload = Map.of("settlementId", aggregateId.toString(), "amountUnits", 1_000);

        publisher.publish(EventAggregateType.SETTLEMENT, aggregateId, "SETTLEMENT_COMPLETED", payload);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertEquals(EventAggregateType.SETTLEMENT, saved.getAggregateType());
        assertEquals(aggregateId, saved.getAggregateId());
        assertEquals("SETTLEMENT_COMPLETED", saved.getEventType());
        assertEquals(payload, saved.getPayload());
    }
}
