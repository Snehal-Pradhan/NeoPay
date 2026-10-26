package com.snehal.neopay.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VaultChargeResponse(
        String type,
        String processorReference,
        String bankReference,
        String errorCode,
        String errorDescription
) {

    public static VaultChargeResponse from(PaymentProcessorResponse response) {
        return switch (response) {
            case PaymentProcessorResponse.Pending pending ->
                    new VaultChargeResponse("pending", pending.processorReference(), null, null, null);
            case PaymentProcessorResponse.Success success ->
                    new VaultChargeResponse("success", success.processorReference(), success.bankReference(), null, null);
            case PaymentProcessorResponse.Failure failure ->
                    new VaultChargeResponse("failure", null, null, failure.errorCode(), failure.errorDescription());
        };
    }
}
