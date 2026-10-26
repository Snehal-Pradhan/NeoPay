package com.snehal.neopay.payment.eventsourcing;

import java.util.UUID;

public class OptimisticConcurrencyException extends RuntimeException {

    public OptimisticConcurrencyException(UUID aggregateId, int expected, int actual) {
        super("Concurrent modification of aggregate " + aggregateId
                + ": expected version " + expected + " but found " + actual);
    }
}
