package com.snehal.neopay.payment.webhook;

import com.snehal.neopay.common.exception.BusinessRuleViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Verifies {@code Stripe-Signature} headers.
 *
 * <p>The header format is {@code t=<unix-seconds>,v1=<hex-hmac>[,v1=<hex-hmac>]}. The
 * signed payload is the concatenation {@code <t>.<raw-body>}, HMAC-SHA256 keyed with the
 * endpoint secret. A signature is accepted only if the timestamp is inside the tolerance
 * window and at least one {@code v1} value matches in constant time.
 */
@Component
public class StripeWebhookVerifier {

    public static final String SIGNATURE_HEADER = "Stripe-Signature";
    private static final Duration DEFAULT_TOLERANCE = Duration.ofSeconds(300);
    private static final String ALGORITHM = "HmacSHA256";

    private final String secret;
    private final Duration tolerance = DEFAULT_TOLERANCE;

    public StripeWebhookVerifier(@Value("${stripe.sandbox.webhook-secret:}") String secret) {
        this.secret = secret;
    }

    public void verify(String payload, String signatureHeader, Instant now) {
        requireConfigured();
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw missingSignature();
        }
        long timestamp = parseTimestamp(signatureHeader);
        requireFresh(timestamp, now);
        List<String> candidates = parseSignatures(signatureHeader);
        if (candidates.isEmpty()) {
            throw missingSignature();
        }
        if (!matchesAny(timestamp + "." + payload, candidates)) {
            throw new BusinessRuleViolationException("WEBHOOK_SIGNATURE_INVALID",
                    "Signature does not match the payload");
        }
    }

    private void requireConfigured() {
        if (secret == null || secret.isBlank()) {
            throw new BusinessRuleViolationException("WEBHOOK_SECRET_MISSING",
                    "Webhook endpoint secret is not configured");
        }
    }

    private void requireFresh(long timestamp, Instant now) {
        long skew = Math.abs(now.getEpochSecond() - timestamp);
        if (skew > tolerance.toSeconds()) {
            throw new BusinessRuleViolationException("WEBHOOK_TIMESTAMP_OUT_OF_TOLERANCE",
                    "Signature timestamp is outside the " + tolerance.toSeconds() + "s tolerance");
        }
    }

    private boolean matchesAny(String signedPayload, List<String> candidates) {
        byte[] expected = sign(signedPayload);
        for (String candidate : candidates) {
            byte[] provided;
            try {
                provided = HexFormat.of().parseHex(candidate);
            } catch (IllegalArgumentException malformed) {
                continue;
            }
            if (MessageDigest.isEqual(expected, provided)) {
                return true;
            }
        }
        return false;
    }

    private static BusinessRuleViolationException missingSignature() {
        return new BusinessRuleViolationException("WEBHOOK_SIGNATURE_MISSING",
                "Missing " + SIGNATURE_HEADER + " header");
    }

    private long parseTimestamp(String header) {
        for (String part : header.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && "t".equals(pair[0])) {
                try {
                    return Long.parseLong(pair[1]);
                } catch (NumberFormatException malformed) {
                    break;
                }
            }
        }
        throw new BusinessRuleViolationException("WEBHOOK_SIGNATURE_MALFORMED",
                "Unable to read timestamp from " + SIGNATURE_HEADER);
    }

    private List<String> parseSignatures(String header) {
        List<String> values = new ArrayList<>();
        for (String part : header.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && "v1".equals(pair[0]) && !pair[1].isBlank()) {
                values.add(pair[1]);
            }
        }
        return values;
    }

    private byte[] sign(String signedPayload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException failure) {
            throw new IllegalStateException("HmacSHA256 is unavailable", failure);
        }
    }

    /** Exposed for tests and tooling that produce a valid header. */
    public String signatureHeader(String payload, Instant at) {
        byte[] digest = sign(at.getEpochSecond() + "." + payload);
        return "t=" + at.getEpochSecond() + ",v1=" + HexFormat.of().formatHex(digest);
    }
}
