package com.snehal.neopay.payment.eventsourcing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DomainEventStore {

    private final DomainEventRepository repository;
    private final List<EventUpcaster> upcasters;

    @Transactional(readOnly = true)
    public int latestSequence(UUID aggregateId) {
        return repository.findTopByAggregateIdOrderBySequenceNumberDesc(aggregateId)
                .map(DomainEventRecord::getSequenceNumber)
                .orElse(0);
    }

    @Transactional
    public void append(UUID aggregateId, String aggregateType, int expectedVersion,
                       List<Map<String, Object>> payloads, String eventType) {
        int current = latestSequence(aggregateId);
        if (current != expectedVersion) {
            throw new OptimisticConcurrencyException(aggregateId, expectedVersion, current);
        }

        int sequence = current;
        List<DomainEventRecord> records = new ArrayList<>(payloads.size());
        for (Map<String, Object> payload : payloads) {
            sequence++;
            records.add(DomainEventRecord.builder()
                    .aggregateId(aggregateId)
                    .aggregateType(aggregateType)
                    .sequenceNumber(sequence)
                    .eventType(eventType)
                    .schemaVersion(2)
                    .payload(payload)
                    .occurredAt(LocalDateTime.now())
                    .build());
        }
        repository.saveAll(records);
    }

    @Transactional(readOnly = true)
    public List<EventEnvelope> load(UUID aggregateId) {
        return repository.findByAggregateIdOrderBySequenceNumberAsc(aggregateId).stream()
                .map(this::toEnvelope)
                .toList();
    }

    private EventEnvelope toEnvelope(DomainEventRecord record) {
        Map<String, Object> payload = record.getPayload();
        int version = record.getSchemaVersion();
        for (EventUpcaster upcaster : upcasters) {
            if (upcaster.supports(record.getEventType(), version)) {
                payload = upcaster.upcast(payload);
                version = ActorUpcaster.TARGET_VERSION;
            }
        }
        return new EventEnvelope(record.getEventType(), version, record.getSequenceNumber(),
                payload, record.getOccurredAt());
    }
}
