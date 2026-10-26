package com.snehal.neopay.payment.dto.request;

import com.snehal.neopay.common.entity.Money;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRefundRequest(
        @NotNull(message = "Refund amount is required")
        Money amount,

        @Size(max = 255, message = "Reason must be at most 255 characters")
        String reason
) {
}
