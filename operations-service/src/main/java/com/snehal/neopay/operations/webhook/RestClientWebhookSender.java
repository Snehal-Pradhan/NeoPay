package com.snehal.neopay.operations.webhook;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class RestClientWebhookSender implements WebhookSender {

    private final RestClient restClient;

    @Value("${webhook.delivery.signature-header:X-NeoPay-Signature}")
    private String signatureHeader;

    @Override
    public int post(String url, String signature, Map<String, Object> body) {
        return restClient.post()
                .uri(url)
                .header(signatureHeader, signature)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode()
                .value();
    }
}
