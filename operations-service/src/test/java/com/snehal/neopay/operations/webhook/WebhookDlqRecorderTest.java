package com.snehal.neopay.operations.webhook;

import com.snehal.neopay.common.enums.WebhookEventStatus;
import com.snehal.neopay.operations.entity.DlqEvent;
import com.snehal.neopay.operations.entity.WebhookEvent;
import com.snehal.neopay.operations.repository.DlqEventRepository;
import com.snehal.neopay.operations.repository.WebhookEventRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WebhookDlqRecorderTest {

    private final WebhookEventRepository webhookRepository = mock(WebhookEventRepository.class);
    private final DlqEventRepository dlqRepository = mock(DlqEventRepository.class);
    private final WebhookDlqRecorder recorder = new WebhookDlqRecorder(webhookRepository, dlqRepository);

    @Test
    void exhaustedAttemptsMarkDeadAndRecord() {
        WebhookEvent event = event();

        recorder.recordAfterAttemptsExhausted(event, "500");

        assertEquals(WebhookEventStatus.DEAD, event.getStatus());
        verify(webhookRepository).save(event);

        ArgumentCaptor<DlqEvent> captor = ArgumentCaptor.forClass(DlqEvent.class);
        verify(dlqRepository).save(captor.capture());
        assertEquals("500", captor.getValue().getFinalError());
    }

    @Test
    void consumerFailureRecordsWithoutWebhookEvent() {
        UUID merchantId = UUID.randomUUID();
        ConsumerRecord<String, Map<String, Object>> record = new ConsumerRecord<>(
                "payments.events", 0, 1, "key",
                Map.of("data", Map.of("merchantId", merchantId.toString())));

        recorder.recordConsumerFailed(record, "db down");

        ArgumentCaptor<DlqEvent> captor = ArgumentCaptor.forClass(DlqEvent.class);
        verify(dlqRepository).save(captor.capture());
        assertNull(captor.getValue().getWebhookEvent());
        assertEquals(merchantId, captor.getValue().getMerchantId());
        assertEquals("db down", captor.getValue().getFinalError());
    }

    @Test
    void malformedEnvelopeStillRecords() {
        ConsumerRecord<String, Map<String, Object>> record = new ConsumerRecord<>(
                "payments.events", 0, 2, "key", Map.of("noData", true));

        recorder.recordConsumerFailed(record, "boom");

        ArgumentCaptor<DlqEvent> captor = ArgumentCaptor.forClass(DlqEvent.class);
        verify(dlqRepository).save(captor.capture());
        assertNull(captor.getValue().getMerchantId());
    }

    @Test
    void nullEnvelopeStillRecords() {
        ConsumerRecord<String, Map<String, Object>> record = new ConsumerRecord<>(
                "payments.events", 0, 3, "key", null);

        recorder.recordConsumerFailed(record, "boom");

        verify(dlqRepository).save(any(DlqEvent.class));
    }

    private static WebhookEvent event() {
        return WebhookEvent.builder()
                .id(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .eventType("PAYMENT_STATUS_CHANGED")
                .payload(Map.of("paymentId", UUID.randomUUID().toString()))
                .targetUrl("https://example.com")
                .signature("sig")
                .status(WebhookEventStatus.FAILED)
                .attempts(7)
                .build();
    }
}
