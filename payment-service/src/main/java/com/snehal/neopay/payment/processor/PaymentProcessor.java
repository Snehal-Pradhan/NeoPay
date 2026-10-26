package com.snehal.neopay.payment.processor;


import com.snehal.neopay.common.dto.PaymentProcessorRequest;
import com.snehal.neopay.common.dto.PaymentProcessorResponse;

public interface PaymentProcessor {

    PaymentProcessorResponse charge(PaymentProcessorRequest request);

}
