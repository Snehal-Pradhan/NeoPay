package com.snehal.neopay.payment.gateway.dto;


import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.PaymentMethod;

import java.util.Map;
import java.util.UUID;

public record PaymentRequest(
        UUID paymentId,
        UUID orderId,
        UUID merchantId,
        Money amount,
        PaymentMethod method,
        Map<String, Object> methodDetails
) {
}
