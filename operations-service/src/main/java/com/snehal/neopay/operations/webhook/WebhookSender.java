package com.snehal.neopay.operations.webhook;

import java.util.Map;

/**
 * Outbound webhook transport. Separated from delivery policy so retry, backoff
 * and dead-letter behaviour can be tested without an HTTP server.
 */
public interface WebhookSender {

    /**
     * Posts a signed payload and returns the HTTP status code.
     */
    int post(String url, String signature, Map<String, Object> body);
}
