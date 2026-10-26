package com.snehal.neopay.payment.eventsourcing;

import java.time.LocalDateTime;
import java.util.Map;

public record EventEnvelope(
        String eventType,
        int schemaVersion,
        int sequence,
        Map<String, Object> payload,
        LocalDateTime occurredAt
) {
    public EventEnvelope withVersion(int version) {
        return new EventEnvelope(eventType, version, sequence, payload, occurredAt);
    }

    public EventEnvelope withPayload(Map<String, Object> newPayload) {
        return new EventEnvelope(eventType, schemaVersion, sequence, newPayload, occurredAt);
    }
}
