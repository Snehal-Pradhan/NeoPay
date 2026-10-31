package com.snehal.neopay.payment.webhook;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Inserts the event row in its own transaction so a unique-constraint violation
 * (a redelivery racing the first delivery) does not poison the caller's context.
 */
@Component
@RequiredArgsConstructor
public class StripeWebhookClaimer {

    private final StripeWebhookEventRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StripeWebhookEvent claim(String externalEventId, String eventType, String payload) {
        return repository.save(StripeWebhookEvent.builder()
                .provider(StripeWebhookService.PROVIDER)
                .externalEventId(externalEventId)
                .eventType(eventType)
                .payload(payload)
                .status(WebhookStatus.RECEIVED)
                .receivedAt(LocalDateTime.now())
                .build());
    }
}
