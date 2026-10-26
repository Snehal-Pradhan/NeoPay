package com.snehal.neopay.payment.gateway;


import com.snehal.neopay.payment.gateway.dto.PaymentRequest;
import com.snehal.neopay.payment.gateway.dto.PaymentResult;

import java.util.UUID;

public interface PaymentAdapter {

    PaymentResult initiate(PaymentRequest request);

    PaymentResult capture(UUID paymentId);
}
