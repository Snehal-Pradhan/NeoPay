package com.snehal.neopay.operations.webhook;

import com.snehal.neopay.common.dto.WebhookTarget;
import com.snehal.neopay.common.util.SignerUtil;
import com.snehal.neopay.operations.client.MerchantServiceClient;
import com.snehal.neopay.operations.entity.WebhookEvent;
import com.snehal.neopay.operations.repository.WebhookEventRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookKafkaConsumerTest {

    private MerchantServiceClient merchantClient;
    private ObjectMapper objectMapper;
    private WebhookEventRepository repository;
    private WebhookRetryQueue retryQueue;
    private WebhookDlqRecorder dlqRecorder;
    private WebhookKafkaConsumer consumer;
    private Acknowledgment ack;

    @BeforeEach
    void setUp() {
        merchantClient = mock(MerchantServiceClient.class);
        objectMapper = mock(ObjectMapper.class);
        repository = mock(WebhookEventRepository.class);
        retryQueue = mock(WebhookRetryQueue.class);
        dlqRecorder = mock(WebhookDlqRecorder.class);
        ack = mock(Acknowledgment.class);
        consumer = new WebhookKafkaConsumer(merchantClient, objectMapper, new SignerUtil(),
                repository, retryQueue, dlqRecorder);
    }

    @Test
    void createsWebhookPerTargetAndAcknowledges() throws Exception {
        UUID merchantId = UUID.randomUUID();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(merchantClient.getActiveConfigsForEvent(eq(merchantId), anyString()))
                .thenReturn(List.of(
                        new WebhookTarget(UUID.randomUUID(), "https://a.example", "secret-a"),
                        new WebhookTarget(UUID.randomUUID(), "https://b.example", "secret-b")));
        when(repository.save(any(WebhookEvent.class))).thenAnswer(invocation -> {
            WebhookEvent event = invocation.getArgument(0);
            event.setId(UUID.randomUUID());
            return event;
        });

        consumer.onWebhookEvent(record(merchantId), ack);

        verify(repository, org.mockito.Mockito.times(2)).save(any(WebhookEvent.class));
        verify(retryQueue, org.mockito.Mockito.times(2)).enqueue(any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void missingMerchantIdIsSkipped() {
        ConsumerRecord<String, Map<String, Object>> record = new ConsumerRecord<>(
                "payments.events", 0, 1, "key",
                Map.of("data", Map.of(), "eventType", "PAYMENT_STATUS_CHANGED"));

        consumer.onWebhookEvent(record, ack);

        verify(repository, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void noTargetsIsAcknowledged() {
        UUID merchantId = UUID.randomUUID();
        when(merchantClient.getActiveConfigsForEvent(eq(merchantId), anyString()))
                .thenReturn(List.of());

        consumer.onWebhookEvent(record(merchantId), ack);

        verify(repository, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void logicalErrorGoesToDeadLetter() {
        UUID merchantId = UUID.randomUUID();
        when(merchantClient.getActiveConfigsForEvent(eq(merchantId), anyString()))
                .thenThrow(new IllegalStateException("lookup failed"));

        consumer.onWebhookEvent(record(merchantId), ack);

        verify(dlqRecorder).recordConsumerFailed(any(), anyString());
        verify(ack).acknowledge();
    }

    @Test
    void databaseFailureLeavesOffsetUnacknowledged() {
        UUID merchantId = UUID.randomUUID();
        when(merchantClient.getActiveConfigsForEvent(eq(merchantId), anyString()))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        consumer.onWebhookEvent(record(merchantId), ack);

        verify(ack, never()).acknowledge();
    }

    private static ConsumerRecord<String, Map<String, Object>> record(UUID merchantId) {
        return new ConsumerRecord<>("payments.events", 0, 1, "key",
                Map.of("data", Map.of("merchantId", merchantId.toString()),
                        "eventType", "PAYMENT_STATUS_CHANGED"));
    }
}
