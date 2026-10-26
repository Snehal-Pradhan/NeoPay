package com.snehal.neopay.operations.webhook;

import com.snehal.neopay.common.enums.WebhookEventStatus;
import com.snehal.neopay.operations.entity.WebhookEvent;
import com.snehal.neopay.operations.repository.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookDeliverExecutorTest {

    private WebhookEventRepository repository;
    private WebhookRetryQueue retryQueue;
    private WebhookSender sender;
    private WebhookDlqRecorder dlqRecorder;
    private WebhookMetrics metrics;
    private WebhookDeliverExecutor executor;

    @BeforeEach
    void setUp() {
        repository = mock(WebhookEventRepository.class);
        retryQueue = mock(WebhookRetryQueue.class);
        sender = mock(WebhookSender.class);
        dlqRecorder = mock(WebhookDlqRecorder.class);
        metrics = mock(WebhookMetrics.class);
        executor = new WebhookDeliverExecutor(repository, retryQueue, sender, dlqRecorder, metrics);
    }

    @Test
    void successfulDeliveryMarksDelivered() {
        WebhookEvent event = event(1);
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        when(sender.post(anyString(), anyString(), any())).thenReturn(200);

        executor.deliver(event.getId());

        assertEquals(WebhookEventStatus.DELIVERED, event.getStatus());
        assertNotNull(event.getDeliveredAt());
        assertEquals(200, event.getLastResponseCode());
        verify(metrics).delivered();
        verify(repository).save(event);
    }

    @Test
    void failureSchedulesRetry() {
        WebhookEvent event = event(1);
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        when(sender.post(anyString(), anyString(), any())).thenReturn(500);

        executor.deliver(event.getId());

        assertEquals(WebhookEventStatus.FAILED, event.getStatus());
        assertNotNull(event.getNextRetryAt());
        verify(retryQueue).enqueue(any(), any());
        verify(metrics).failed("FAILED");
    }

    @Test
    void exhaustedAttemptsMoveToDeadLetter() {
        WebhookEvent event = event(7);
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        when(sender.post(anyString(), anyString(), any())).thenReturn(500);

        executor.deliver(event.getId());

        assertEquals(WebhookEventStatus.DEAD, event.getStatus());
        verify(dlqRecorder).recordAfterAttemptsExhausted(any(), anyString());
        verify(metrics).failed("DEAD");
    }

    @Test
    void transportFailureSchedulesRetry() {
        WebhookEvent event = event(1);
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        when(sender.post(anyString(), anyString(), any()))
                .thenThrow(new RestClientException("timeout"));

        executor.deliver(event.getId());

        assertEquals(WebhookEventStatus.FAILED, event.getStatus());
        verify(retryQueue).enqueue(any(), any());
    }

    @Test
    void missingEventIsIgnored() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        executor.deliver(id);

        verify(repository, never()).save(any());
        verify(sender, never()).post(any(), any(), any());
    }

    @Test
    void terminalEventIsIgnored() {
        WebhookEvent event = event(1);
        event.setStatus(WebhookEventStatus.DELIVERED);
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));

        executor.deliver(event.getId());

        verify(repository, never()).save(any());
        verify(metrics, never()).delivered();
        verify(sender, never()).post(any(), any(), any());
    }

    @Test
    void payloadIsForwardedWithSignature() {
        WebhookEvent event = event(1);
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        when(sender.post(anyString(), anyString(), any())).thenReturn(200);

        executor.deliver(event.getId());

        verify(sender).post(eq("https://example.com/hook"), eq("sig"), any());
    }

    private static WebhookEvent event(int attempts) {
        return WebhookEvent.builder()
                .id(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .eventType("PAYMENT_STATUS_CHANGED")
                .payload(Map.of("paymentId", UUID.randomUUID().toString()))
                .targetUrl("https://example.com/hook")
                .signature("sig")
                .status(WebhookEventStatus.PENDING)
                .attempts(attempts)
                .build();
    }
}
