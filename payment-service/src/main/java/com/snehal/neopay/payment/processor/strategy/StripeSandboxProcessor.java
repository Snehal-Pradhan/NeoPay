package com.snehal.neopay.payment.processor.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snehal.neopay.common.dto.PaymentProcessorRequest;
import com.snehal.neopay.common.dto.PaymentProcessorResponse;
import com.snehal.neopay.payment.processor.PaymentProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Locale;

/**
 * Stripe-shaped sandbox processor.
 *
 * <p>This adapter exists to prove the processor boundary against a real PSP wire
 * contract without moving money: the request and response mapping follow Stripe's
 * PaymentIntents API, and the contract test runs against WireMock. The default
 * routing still uses the deterministic in-process simulators; switch a method to
 * this processor only when a sandbox key and base URL are configured.
 */
@Component
@Slf4j
public class StripeSandboxProcessor implements PaymentProcessor {

    private static final String FORM = MediaType.APPLICATION_FORM_URLENCODED_VALUE;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public StripeSandboxProcessor(ObjectMapper objectMapper,
                                  @Value("${stripe.sandbox.base-url:https://api.stripe.com}") String baseUrl,
                                  @Value("${stripe.sandbox.api-key:}") String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentProcessorResponse charge(PaymentProcessorRequest request) {
        String form = "amount=" + request.amount().getAmountUnits()
                + "&currency=" + request.amount().getCurrency().toLowerCase(Locale.ROOT)
                + "&confirm=true";

        RestClient.RequestHeadersSpec<?> call = restClient.post()
                .uri("/v1/payment_intents")
                .header("Idempotency-Key", request.paymentId().toString())
                .contentType(MediaType.parseMediaType(FORM))
                .body(form);

        return call.exchange((req, res) -> mapResponse(res.getStatusCode().value(),
                readBody(res.getBody())));
    }

    private String readBody(java.io.InputStream body) {
        try {
            return body == null ? "" : new String(body.readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to read Stripe response", failure);
        }
    }

    private PaymentProcessorResponse mapResponse(int status, String body) {
        JsonNode json = parse(body);
        if (status >= 200 && status < 300) {
            return mapIntent(json);
        }
        String code = json.path("error").path("decline_code").asText("");
        if (code.isBlank()) {
            code = json.path("error").path("code").asText("");
        }
        if (code.isBlank()) {
            code = status == 409 ? "IDEMPOTENCY_CONFLICT" : "STRIPE_ERROR_" + status;
        }
        String message = json.path("error").path("message").asText("Stripe request failed");
        log.warn("Stripe sandbox rejected payment: status={} code={}", status, code);
        return new PaymentProcessorResponse.Failure(code, message);
    }

    private PaymentProcessorResponse mapIntent(JsonNode json) {
        String intentId = json.path("id").asText("stripe_intent");
        String status = json.path("status").asText("");
        if ("succeeded".equals(status)) {
            return new PaymentProcessorResponse.Success(intentId,
                    json.path("latest_charge").asText(null));
        }
        if ("requires_action".equals(status) || "requires_confirmation".equals(status)
                || "processing".equals(status)) {
            return new PaymentProcessorResponse.Pending(intentId);
        }
        return new PaymentProcessorResponse.Failure("STRIPE_INTENT_" + status.toUpperCase(Locale.ROOT),
                "Unhandled PaymentIntent status: " + status);
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(body);
        } catch (IOException malformed) {
            throw new IllegalStateException("Malformed Stripe response", malformed);
        }
    }
}
