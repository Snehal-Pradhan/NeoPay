package com.snehal.neopay.vault.processor;

import com.snehal.neopay.common.dto.PaymentProcessorRequest;
import com.snehal.neopay.common.dto.PaymentProcessorResponse;
import com.snehal.neopay.common.entity.Money;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardPaymentProcessorTest {

    private final CardPaymentProcessor processor = new CardPaymentProcessor();

    private static PaymentProcessorRequest requestFor(String pan) {
        return PaymentProcessorRequest.card(UUID.randomUUID(), pan, "12/2030",
                Money.inr(1_000), Map.of());
    }

    @Test
    void declinedTestPanMapsToDeclinedFailure() {
        PaymentProcessorResponse response =
                processor.charge(requestFor(CardPaymentProcessor.PAN_CARD_DECLINED)).join();

        PaymentProcessorResponse.Failure failure =
                assertInstanceOf(PaymentProcessorResponse.Failure.class, response);
        assertEquals("CARD_DECLINED", failure.errorCode());
    }

    @Test
    void expiredTestPanMapsToExpiredFailure() {
        PaymentProcessorResponse response =
                processor.charge(requestFor(CardPaymentProcessor.PAN_CARD_EXPIRED)).join();

        PaymentProcessorResponse.Failure failure =
                assertInstanceOf(PaymentProcessorResponse.Failure.class, response);
        assertEquals("CARD_EXPIRED", failure.errorCode());
    }

    @Test
    void ordinaryPanReturnsPendingWithProcessorReference() {
        PaymentProcessorResponse response = processor.charge(requestFor("4111111111111111")).join();

        PaymentProcessorResponse.Pending pending =
                assertInstanceOf(PaymentProcessorResponse.Pending.class, response);
        assertTrue(pending.processorReference().startsWith("CARD_PROCESSOR_"));
    }
}
