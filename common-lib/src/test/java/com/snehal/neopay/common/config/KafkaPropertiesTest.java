package com.snehal.neopay.common.config;

import com.snehal.neopay.common.enums.EventAggregateType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KafkaPropertiesTest {

    @Test
    void resolvesTopicByLowercaseAggregateName() {
        KafkaProperties properties = new KafkaProperties();
        properties.setTopics(Map.of("payment", "payments.events", "order", "orders.events"));

        assertEquals("payments.events", properties.topicFor(EventAggregateType.PAYMENT));
        assertEquals("orders.events", properties.topicFor(EventAggregateType.ORDER));
    }

    @Test
    void unknownAggregateThrows() {
        KafkaProperties properties = new KafkaProperties();
        properties.setTopics(Map.of());

        assertThrows(IllegalStateException.class,
                () -> properties.topicFor(EventAggregateType.REFUND));
    }
}
