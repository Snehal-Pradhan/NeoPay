package com.snehal.neopay.payment.dispute;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResolveDisputeRequest(
        @NotNull(message = "Outcome is required") DisputeStatus outcome,
        @Size(max = 255) String reason
) {
}
