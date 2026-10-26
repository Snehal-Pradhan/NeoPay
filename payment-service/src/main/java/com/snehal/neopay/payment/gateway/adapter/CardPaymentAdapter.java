package com.snehal.neopay.payment.gateway.adapter;

import com.snehal.neopay.common.dto.VaultChargeResponse;
import com.snehal.neopay.common.dto.VaultChargeRequest;
import com.snehal.neopay.payment.client.VaultServiceClient;
import com.snehal.neopay.payment.gateway.PaymentAdapter;
import com.snehal.neopay.payment.gateway.dto.PaymentRequest;
import com.snehal.neopay.payment.gateway.dto.PaymentResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@RequiredArgsConstructor
@Component
public class CardPaymentAdapter implements PaymentAdapter {

    private final VaultServiceClient vaultServiceClient;

    @Override
    @CircuitBreaker(name = "vault-service")
    @Retry(name = "vault-service")
    public PaymentResult initiate(PaymentRequest request) {
        String token = (String) request.methodDetails().get("token");

        VaultChargeResponse response = vaultServiceClient.charge(
                new VaultChargeRequest(request.paymentId(), token, request.amount(), request.methodDetails())
        );

        return switch (response.type()) {
            case "success" -> new PaymentResult.Success(response.bankReference());
            case "failure" -> new PaymentResult.Failure(response.errorCode(), response.errorDescription());
            default -> new PaymentResult.Pending(response.processorReference());
        };
    }

    @Override
    public PaymentResult capture(UUID paymentId) {
        return new PaymentResult.Success("CARD_REF");
    }
}
