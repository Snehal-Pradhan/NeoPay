package com.snehal.neopay.payment.dto.response;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.PaymentMethod;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentResponse(
        UUID id,
        UUID orderId,
        UUID merchantId,
        Money amount,
        PaymentStatus status,
        PaymentMethod method,
        Map<String, Object> methodDetails,
        String errorCode,
        String errorDescription,
        LocalDateTime capturedAt,
        LocalDateTime createdAt
) {

}
