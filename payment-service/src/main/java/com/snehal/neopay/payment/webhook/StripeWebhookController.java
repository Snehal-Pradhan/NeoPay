package com.snehal.neopay.payment.webhook;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound PSP callback endpoint. Public at the gateway; authenticated by the
 * {@code Stripe-Signature} HMAC, not by a merchant API key.
 */
@RestController
@RequiredArgsConstructor
public class StripeWebhookController {

    private final StripeWebhookService webhookService;

    @PostMapping("/webhook/stripe")
    public ResponseEntity<StripeWebhookService.WebhookOutcome> receive(
            @RequestBody String payload,
            @RequestHeader(value = StripeWebhookVerifier.SIGNATURE_HEADER, required = false)
            String signature) {

        StripeWebhookService.WebhookOutcome outcome =
                webhookService.receive(payload, signature);
        return ResponseEntity.ok(outcome);
    }
}
