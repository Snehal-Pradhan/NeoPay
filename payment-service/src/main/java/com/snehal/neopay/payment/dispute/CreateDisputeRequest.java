package com.snehal.neopay.payment.dispute;

import com.snehal.neopay.common.entity.Money;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDisputeRequest(
        @NotNull(message = "Dispute amount is required") Money amount,
        @Size(max = 255) String reason
) {
}
