package com.snehal.neopay.payment.eventsourcing;

import com.snehal.neopay.common.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentEventReplayer {

    private final DomainEventStore store;
    private final SnapshotRepository snapshots;

    @Transactional(readOnly = true)
    public PaymentStatus replay(UUID paymentId) {
        var snapshot = snapshots.findById(paymentId);
        PaymentStatus status = snapshot
                .map(s -> PaymentStatus.valueOf(s.getStatus()))
                .orElse(PaymentStatus.CREATED);
        int fromSequence = snapshot
                .map(SnapshotRecord::getSequenceNumber)
                .orElse(0);

        for (EventEnvelope event : store.load(paymentId)) {
            if (event.sequence() > fromSequence) {
                status = PaymentStatus.valueOf(String.valueOf(event.payload().get("toStatus")));
            }
        }
        return status;
    }

    @Transactional(readOnly = true)
    public int eventCount(UUID paymentId) {
        return store.load(paymentId).size();
    }
}
