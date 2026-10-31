package com.snehal.neopay.payment.dispute;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Merchant evidence upload. {@code externalEvidenceId} is the client's idempotency key for
 * the upload, not a database id.
 */
public record EvidenceRequest(
        @NotBlank(message = "Evidence id is required") @Size(max = 128) String externalEvidenceId,
        @NotBlank(message = "Evidence summary is required") @Size(max = 500) String summary
) {
}
