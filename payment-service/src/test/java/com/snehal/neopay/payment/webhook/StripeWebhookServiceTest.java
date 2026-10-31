package com.snehal.neopay.payment.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snehal.neopay.common.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StripeWebhookServiceTest {

    private static final String PAYLOAD = "{\"id\":\"evt_1\",\"type\":\"payment_intent.succeeded\","
            + "\"data\":{\"object\":{\"id\":\"pi_1\"}}}";

    private final StripeWebhookVerifier verifier = mock(StripeWebhookVerifier.class);
    private final StripeWebhookClaimer claimer = mock(StripeWebhookClaimer.class);
    private final StripeWebhookEventRepository repository =
            mock(StripeWebhookEventRepository.class);
    private final StripePaymentEventApplier applier = mock(StripePaymentEventApplier.class);

    private final StripeWebhookService service = new StripeWebhookService(
            verifier, claimer, repository, applier, new ObjectMapper());

    @Test
    void freshEventIsClaimedProcessedAndPersisted() {
        StripeWebhookEvent event = event();
        when(repository.findByProviderAndExternalEventId("STRIPE", "evt_1"))
                .thenReturn(Optional.empty());
        when(claimer.claim("evt_1", "payment_intent.succeeded", PAYLOAD)).thenReturn(event);
        when(applier.apply("payment_intent.succeeded", eventPayload())).thenReturn(true);

        StripeWebhookService.WebhookOutcome outcome = service.receive(PAYLOAD, "sig");

        verify(verifier).verify(eq(PAYLOAD), eq("sig"), any());
        assertEquals("PROCESSED", outcome.status());
        assertFalse(outcome.duplicate());
        assertEquals(WebhookStatus.PROCESSED, event.getStatus());
        verify(repository).save(event);
    }

    @Test
    void acknowledgedWithoutStateChangeIsMarkedIgnored() {
        StripeWebhookEvent event = event();
        when(claimer.claim(anyString(), anyString(), anyString())).thenReturn(event);
        when(applier.apply(anyString(), any())).thenReturn(false);

        StripeWebhookService.WebhookOutcome outcome = service.receive(PAYLOAD, "sig");

        assertEquals("IGNORED", outcome.status());
        assertEquals(WebhookStatus.IGNORED, event.getStatus());
    }

    @Test
    void duplicateDeliverySkipsProcessing() {
        StripeWebhookEvent existing = event();
        existing.markProcessed(java.time.LocalDateTime.now());
        when(repository.findByProviderAndExternalEventId("STRIPE", "evt_1"))
                .thenReturn(Optional.of(existing));

        StripeWebhookService.WebhookOutcome outcome = service.receive(PAYLOAD, "sig");

        assertTrue(outcome.duplicate());
        assertEquals("PROCESSED", outcome.status());
        verify(claimer, never()).claim(anyString(), anyString(), anyString());
        verify(applier, never()).apply(anyString(), any());
    }

    @Test
    void losingTheInsertRaceIsTreatedAsDuplicate() {
        when(repository.findByProviderAndExternalEventId(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(claimer.claim(anyString(), anyString(), anyString()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        StripeWebhookService.WebhookOutcome outcome = service.receive(PAYLOAD, "sig");

        assertTrue(outcome.duplicate());
        verify(applier, never()).apply(anyString(), any());
    }

    @Test
    void processingFailureIsRecordedAndAcknowledged() {
        StripeWebhookEvent event = event();
        when(claimer.claim(anyString(), anyString(), anyString())).thenReturn(event);
        when(applier.apply(anyString(), any()))
                .thenThrow(new IllegalStateException("ledger unavailable"));

        StripeWebhookService.WebhookOutcome outcome = service.receive(PAYLOAD, "sig");

        assertEquals("FAILED", outcome.status());
        assertEquals(WebhookStatus.FAILED, event.getStatus());
        assertEquals("ledger unavailable", event.getError());
        verify(repository).save(event);
    }

    @Test
    void invalidJsonIsRejectedBeforeAnyWrite() {
        assertThrows(BusinessRuleViolationException.class,
                () -> service.receive("{not-json", "sig"));

        verify(claimer, never()).claim(anyString(), anyString(), anyString());
    }

    @Test
    void missingEventIdentityIsRejected() {
        assertThrows(BusinessRuleViolationException.class,
                () -> service.receive("{\"type\":\"payment_intent.succeeded\"}", "sig"));

        verify(claimer, never()).claim(anyString(), anyString(), anyString());
    }

    private static StripeWebhookEvent event() {
        return StripeWebhookEvent.builder()
                .provider(StripeWebhookService.PROVIDER)
                .externalEventId("evt_1")
                .eventType("payment_intent.succeeded")
                .payload(PAYLOAD)
                .status(WebhookStatus.RECEIVED)
                .receivedAt(java.time.LocalDateTime.now())
                .build();
    }

    private static com.fasterxml.jackson.databind.JsonNode eventPayload() {
        try {
            return new ObjectMapper().readTree(PAYLOAD);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }
}
