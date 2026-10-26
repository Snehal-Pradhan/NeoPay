package com.snehal.neopay.payment.eventsourcing;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Upgrades PAYMENT_TRANSITION version 1 to version 2 by adding the actor field,
 * which version 1 did not record.
 */
@Component
public class ActorUpcaster implements EventUpcaster {

    public static final String EVENT_TYPE = "PAYMENT_TRANSITION";
    public static final int TARGET_VERSION = 2;

    @Override
    public boolean supports(String eventType, int schemaVersion) {
        return EVENT_TYPE.equals(eventType) && schemaVersion < TARGET_VERSION;
    }

    @Override
    public Map<String, Object> upcast(Map<String, Object> payload) {
        Map<String, Object> upgraded = new HashMap<>(payload);
        upgraded.putIfAbsent("actor", "SYSTEM");
        return upgraded;
    }
}
