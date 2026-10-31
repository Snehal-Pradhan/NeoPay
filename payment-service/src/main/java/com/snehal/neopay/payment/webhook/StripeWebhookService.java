package com.snehal.neopay.payment.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snehal.neopay.common.exception.BusinessRuleViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Entry point for inbound PSP events.
 *
 * <p>Order of operations: verify the signature, parse, claim the event id, dispatch.
 * Nothing is persisted before the signature check, so forged requests leave no rows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookService {

    public static final String PROVIDER = "STRIPE";

    private final StripeWebhookVerifier verifier;
    private final StripeWebhookClaimer claimer;
    private final StripeWebhookEventRepository repository;
    private final StripePaymentEventApplier applier;
    private final ObjectMapper objectMapper;

    public WebhookOutcome receive(String payload, String signatureHeader) {
        verifier.verify(payload, signatureHeader, Instant.now());

        JsonNode root = parse(payload);
        String eventId = root.path("id").asText("");
        String eventType = root.path("type").asText("");
        if (eventId.isBlank() || eventType.isBlank()) {
            throw new BusinessRuleViolationException("WEBHOOK_PAYLOAD_INVALID",
                    "Event id and type are required");
        }

        var existing = repository.findByProviderAndExternalEventId(PROVIDER, eventId);
        if (existing.isPresent()) {
            log.info("Duplicate webhook delivery for event {} status {}",
                    eventId, existing.get().getStatus());
            return new WebhookOutcome(eventId, existing.get().getStatus().name(), true);
        }

        StripeWebhookEvent event;
        try {
            event = claimer.claim(eventId, eventType, payload);
        } catch (DataIntegrityViolationException raced) {
            log.info("Concurrent duplicate webhook delivery for event {}", eventId);
            return new WebhookOutcome(eventId, WebhookStatus.RECEIVED.name(), true);
        }

        return process(event, root);
    }

    private WebhookOutcome process(StripeWebhookEvent event, JsonNode root) {
        try {
            boolean changed = applier.apply(event.getEventType(), root);
            LocalDateTime now = LocalDateTime.now();
            if (changed) {
                event.markProcessed(now);
            } else {
                event.markIgnored(now);
            }
            repository.save(event);
            return new WebhookOutcome(event.getExternalEventId(), event.getStatus().name(), false);
        } catch (RuntimeException failure) {
            log.warn("Webhook event {} failed: {}", event.getExternalEventId(), failure.getMessage());
            event.markFailed(failure.getMessage(), LocalDateTime.now());
            repository.save(event);
            return new WebhookOutcome(event.getExternalEventId(), WebhookStatus.FAILED.name(), false);
        }
    }

    private JsonNode parse(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException malformed) {
            throw new BusinessRuleViolationException("WEBHOOK_PAYLOAD_INVALID",
                    "Body is not valid JSON");
        }
    }

    /** Result returned to the PSP. A non-2xx response triggers retries, so failures are acked. */
    public record WebhookOutcome(String eventId, String status, boolean duplicate) {
    }
}
