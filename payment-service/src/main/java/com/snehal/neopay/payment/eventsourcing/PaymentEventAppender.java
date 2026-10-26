package com.snehal.neopay.payment.eventsourcing;

import com.snehal.neopay.common.enums.PaymentActor;
import com.snehal.neopay.common.enums.PaymentEvent;
import com.snehal.neopay.common.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentEventAppender {

    static final int SNAPSHOT_INTERVAL = 10;

    private final DomainEventStore store;
    private final SnapshotRepository snapshots;

    @Transactional
    public void record(UUID paymentId, PaymentStatus from, PaymentStatus to,
                       PaymentEvent event, PaymentActor actor) {
        int version = store.latestSequence(paymentId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fromStatus", from.name());
        payload.put("toStatus", to.name());
        payload.put("event", event.name());
        payload.put("actor", actor == null ? PaymentActor.SYSTEM.name() : actor.name());

        store.append(paymentId, "PAYMENT", version, List.of(payload), ActorUpcaster.EVENT_TYPE);

        if ((version + 1) % SNAPSHOT_INTERVAL == 0) {
            saveSnapshot(paymentId, version + 1, to);
        }
    }

    private void saveSnapshot(UUID paymentId, int sequence, PaymentStatus status) {
        SnapshotRecord snapshot = snapshots.findById(paymentId)
                .orElseGet(() -> SnapshotRecord.builder()
                        .aggregateId(paymentId)
                        .createdAt(LocalDateTime.now())
                        .build());
        snapshot.setSequenceNumber(sequence);
        snapshot.setStatus(status.name());
        snapshots.save(snapshot);
    }
}
