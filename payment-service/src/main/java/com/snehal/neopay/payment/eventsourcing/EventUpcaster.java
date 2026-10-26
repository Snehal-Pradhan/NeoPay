package com.snehal.neopay.payment.eventsourcing;

import java.util.Map;

public interface EventUpcaster {

    boolean supports(String eventType, int schemaVersion);

    Map<String, Object> upcast(Map<String, Object> payload);
}
