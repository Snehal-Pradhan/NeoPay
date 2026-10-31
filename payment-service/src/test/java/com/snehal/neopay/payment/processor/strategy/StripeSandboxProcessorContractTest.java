package com.snehal.neopay.payment.processor.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.snehal.neopay.common.dto.PaymentProcessorRequest;
import com.snehal.neopay.common.dto.PaymentProcessorResponse;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.PaymentMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StripeSandboxProcessorContractTest {

    private WireMockServer stripe;
    private StripeSandboxProcessor processor;

    @BeforeEach
    void startStripeStub() {
        stripe = new WireMockServer(options().dynamicPort());
        stripe.start();
        processor = new StripeSandboxProcessor(new ObjectMapper(), stripe.baseUrl(), "sk_test_123");
    }

    @AfterEach
    void stopStripeStub() {
        stripe.stop();
    }

    @Test
    void succeededIntentMapsToSuccessAndSendsIdempotencyKey() {
        UUID paymentId = UUID.randomUUID();
        stripe.stubFor(post(urlEqualTo("/v1/payment_intents"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"pi_123\",\"status\":\"succeeded\","
                                + "\"latest_charge\":\"ch_123\"}")));

        PaymentProcessorResponse response = processor.charge(request(paymentId, 5000));

        PaymentProcessorResponse.Success success =
                assertInstanceOf(PaymentProcessorResponse.Success.class, response);
        assertEquals("pi_123", success.processorReference());
        assertEquals("ch_123", success.bankReference());

        stripe.verify(postRequestedFor(urlEqualTo("/v1/payment_intents"))
                .withHeader("Authorization", equalTo("Bearer sk_test_123"))
                .withHeader("Idempotency-Key", equalTo(paymentId.toString()))
                .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
                .withRequestBody(equalTo("amount=5000&currency=inr&confirm=true"
                        + "&metadata[payment_id]=" + paymentId)));
    }

    @Test
    void requiresActionIntentMapsToPending() {
        stripe.stubFor(post(urlEqualTo("/v1/payment_intents"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"pi_3ds\",\"status\":\"requires_action\"}")));

        PaymentProcessorResponse response = processor.charge(request(UUID.randomUUID(), 5000));

        PaymentProcessorResponse.Pending pending =
                assertInstanceOf(PaymentProcessorResponse.Pending.class, response);
        assertEquals("pi_3ds", pending.processorReference());
    }

    @Test
    void declinedCardMapsToDeclineCode() {
        stripe.stubFor(post(urlEqualTo("/v1/payment_intents"))
                .willReturn(aResponse().withStatus(402)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"type\":\"card_error\",\"code\":\"card_declined\","
                                + "\"decline_code\":\"insufficient_funds\","
                                + "\"message\":\"Your card has insufficient funds.\"}}")));

        PaymentProcessorResponse response = processor.charge(request(UUID.randomUUID(), 5000));

        PaymentProcessorResponse.Failure failure =
                assertInstanceOf(PaymentProcessorResponse.Failure.class, response);
        assertEquals("insufficient_funds", failure.errorCode());
        assertTrue(failure.errorDescription().contains("insufficient funds"));
    }

    @Test
    void idempotencyConflictMapsToFailure() {
        stripe.stubFor(post(urlEqualTo("/v1/payment_intents"))
                .willReturn(aResponse().withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"type\":\"idempotency_error\","
                                + "\"message\":\"Keys for idempotent requests must match.\"}}")));

        PaymentProcessorResponse response = processor.charge(request(UUID.randomUUID(), 5000));

        PaymentProcessorResponse.Failure failure =
                assertInstanceOf(PaymentProcessorResponse.Failure.class, response);
        assertEquals("IDEMPOTENCY_CONFLICT", failure.errorCode());
    }

    @Test
    void unexpectedIntentStatusMapsToFailure() {
        stripe.stubFor(post(urlEqualTo("/v1/payment_intents"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"pi_canceled\",\"status\":\"canceled\"}")));

        PaymentProcessorResponse response = processor.charge(request(UUID.randomUUID(), 5000));

        PaymentProcessorResponse.Failure failure =
                assertInstanceOf(PaymentProcessorResponse.Failure.class, response);
        assertEquals("STRIPE_INTENT_CANCELED", failure.errorCode());
    }

    private static PaymentProcessorRequest request(UUID paymentId, long amountUnits) {
        return new PaymentProcessorRequest(UUID.randomUUID(), paymentId, PaymentMethod.CARD,
                Money.inr(amountUnits), "4111111111111111", "12/30", Map.of());
    }
}
