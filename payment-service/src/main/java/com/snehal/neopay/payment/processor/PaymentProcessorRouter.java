package com.snehal.neopay.payment.processor;

import com.snehal.neopay.common.dto.PaymentProcessorRequest;
import com.snehal.neopay.common.dto.PaymentProcessorResponse;
import com.snehal.neopay.common.enums.PaymentMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class PaymentProcessorRouter {

    private final Map<PaymentMethod, PaymentProcessor> paymentProcessors;

    public PaymentProcessorResponse charge(PaymentProcessorRequest request) {
        PaymentProcessor processor = paymentProcessors.get(request.method());
        if (processor == null) {
            throw new IllegalArgumentException("No payment processor registered for method: "+request.method());
        }
        return processor.charge(request);
    }
}
