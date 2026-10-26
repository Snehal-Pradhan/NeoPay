package com.snehal.neopay.common.dto;


import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.PaymentMethod;

import java.util.Map;
import java.util.UUID;

public record PaymentProcessorRequest(
        UUID processingId,
        UUID paymentId,
        PaymentMethod method,
        Money amount,
        String pan,
        String expiry,
        Map<String, Object> methodDetails
) {

    public static PaymentProcessorRequest card(UUID paymentId, String pan, String expiry, Money amount, Map<String, Object> details) {
        return new PaymentProcessorRequest(UUID.randomUUID(), paymentId, PaymentMethod.CARD, amount,
                pan, expiry, details);
    }

    @Override
    public String toString() {
        String maskedPan = pan == null || pan.length() < 4
                ? null
                : "****" + pan.substring(pan.length() - 4);
        return "PaymentProcessorRequest[processingId=" + processingId
                + ", paymentId=" + paymentId + ", method=" + method
                + ", amount=" + amount + ", pan=" + maskedPan + "]";
    }

    public static PaymentProcessorRequest nonCard(UUID paymentId, PaymentMethod method, Money amount, Map<String, Object> details) {
        return new PaymentProcessorRequest(UUID.randomUUID(), paymentId, method, amount,
                null, null, details);
    }


}
