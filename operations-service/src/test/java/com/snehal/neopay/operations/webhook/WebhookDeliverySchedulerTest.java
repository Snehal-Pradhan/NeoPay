package com.snehal.neopay.operations.webhook;

import com.snehal.neopay.common.enums.WebhookEventStatus;
import com.snehal.neopay.operations.entity.WebhookEvent;
import com.snehal.neopay.operations.repository.WebhookEventRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookDeliverySchedulerTest {

    @Test
    void dueEventsAreDelivered() {
        WebhookRetryQueue queue = mock(WebhookRetryQueue.class);
        WebhookEventRepository repository = mock(WebhookEventRepository.class);
        WebhookDeliverExecutor executor = mock(WebhookDeliverExecutor.class);
        WebhookDeliveryScheduler scheduler = new WebhookDeliveryScheduler(queue, repository, executor);
        scheduler.init();

        UUID id = UUID.randomUUID();
        when(queue.pollDue(anyInt())).thenReturn(Set.of(id));

        scheduler.pollAndDeliver();

        verify(executor, timeout(1000)).deliver(id);
        scheduler.shutdown();
    }

    @Test
    void emptyQueueDoesNothing() {
        WebhookRetryQueue queue = mock(WebhookRetryQueue.class);
        WebhookEventRepository repository = mock(WebhookEventRepository.class);
        WebhookDeliverExecutor executor = mock(WebhookDeliverExecutor.class);
        WebhookDeliveryScheduler scheduler = new WebhookDeliveryScheduler(queue, repository, executor);
        scheduler.init();

        when(queue.pollDue(anyInt())).thenReturn(Set.of());

        scheduler.pollAndDeliver();

        verify(executor, org.mockito.Mockito.never()).deliver(any());
        scheduler.shutdown();
    }

    @Test
    void reconcileRequeuesPendingEvents() {
        WebhookRetryQueue queue = mock(WebhookRetryQueue.class);
        WebhookEventRepository repository = mock(WebhookEventRepository.class);
        WebhookDeliverExecutor executor = mock(WebhookDeliverExecutor.class);
        WebhookDeliveryScheduler scheduler = new WebhookDeliveryScheduler(queue, repository, executor);

        WebhookEvent event = WebhookEvent.builder()
                .id(UUID.randomUUID())
                .status(WebhookEventStatus.PENDING)
                .nextRetryAt(LocalDateTime.now())
                .build();
        when(repository.findByStatusAndNextRetryAtBefore(eq(WebhookEventStatus.PENDING), any()))
                .thenReturn(List.of(event));

        scheduler.reconcileFromDatabase();

        verify(queue).enqueueIfAbsent(eq(event.getId()), any());
    }
}
