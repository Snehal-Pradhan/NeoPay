package com.snehal.neopay.payment.webhook;

import com.snehal.neopay.common.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StripeWebhookVerifierTest {

    private static final String SECRET = "whsec_test_secret";
    private static final String BODY = "{\"id\":\"evt_1\",\"type\":\"payment_intent.succeeded\"}";

    private final StripeWebhookVerifier verifier = new StripeWebhookVerifier(SECRET);

    @Test
    void validSignatureIsAccepted() {
        Instant now = Instant.parse("2026-10-31T10:00:00Z");
        String header = verifier.signatureHeader(BODY, now);

        assertDoesNotThrow(() -> verifier.verify(BODY, header, now));
    }

    @Test
    void tamperedPayloadIsRejected() {
        Instant now = Instant.parse("2026-10-31T10:00:00Z");
        String header = verifier.signatureHeader(BODY, now);

        BusinessRuleViolationException error = assertThrows(BusinessRuleViolationException.class,
                () -> verifier.verify(BODY + " ", header, now));

        assertEquals("WEBHOOK_SIGNATURE_INVALID", error.getErrorCode());
    }

    @Test
    void staleTimestampIsRejected() {
        Instant signedAt = Instant.parse("2026-10-31T09:00:00Z");
        String header = verifier.signatureHeader(BODY, signedAt);

        BusinessRuleViolationException error = assertThrows(BusinessRuleViolationException.class,
                () -> verifier.verify(BODY, header, signedAt.plusSeconds(301)));

        assertEquals("WEBHOOK_TIMESTAMP_OUT_OF_TOLERANCE", error.getErrorCode());
    }

    @Test
    void anyMatchingV1SignatureIsAccepted() {
        Instant now = Instant.parse("2026-10-31T10:00:00Z");
        String valid = verifier.signatureHeader(BODY, now).split("v1=")[1];
        String header = "t=" + now.getEpochSecond() + ",v1=00ff,v1=" + valid;

        assertDoesNotThrow(() -> verifier.verify(BODY, header, now));
    }

    @Test
    void missingHeaderIsRejected() {
        Instant now = Instant.parse("2026-10-31T10:00:00Z");

        BusinessRuleViolationException error = assertThrows(BusinessRuleViolationException.class,
                () -> verifier.verify(BODY, null, now));

        assertEquals("WEBHOOK_SIGNATURE_MISSING", error.getErrorCode());
    }

    @Test
    void malformedTimestampIsRejected() {
        Instant now = Instant.parse("2026-10-31T10:00:00Z");

        BusinessRuleViolationException error = assertThrows(BusinessRuleViolationException.class,
                () -> verifier.verify(BODY, "t=not-a-number,v1=abcd", now));

        assertEquals("WEBHOOK_SIGNATURE_MALFORMED", error.getErrorCode());
    }

    @Test
    void headerWithoutV1IsRejected() {
        Instant now = Instant.parse("2026-10-31T10:00:00Z");

        BusinessRuleViolationException error = assertThrows(BusinessRuleViolationException.class,
                () -> verifier.verify(BODY, "t=" + now.getEpochSecond(), now));

        assertEquals("WEBHOOK_SIGNATURE_MISSING", error.getErrorCode());
    }

    @Test
    void missingSecretFailsClosed() {
        StripeWebhookVerifier unconfigured = new StripeWebhookVerifier(" ");
        Instant now = Instant.parse("2026-10-31T10:00:00Z");

        BusinessRuleViolationException error = assertThrows(BusinessRuleViolationException.class,
                () -> unconfigured.verify(BODY, "t=" + now.getEpochSecond() + ",v1=abcd", now));

        assertEquals("WEBHOOK_SECRET_MISSING", error.getErrorCode());
    }
}
