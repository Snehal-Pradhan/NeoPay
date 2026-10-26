package com.snehal.neopay.payment.dto.response;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.RefundStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RefundResponse(
        UUID id,
        UUID paymentId,
        Money amount,
        RefundStatus status,
        String reason,
        String bankReference,
        LocalDateTime createdAt,
        LocalDateTime processedAt
) {
}
