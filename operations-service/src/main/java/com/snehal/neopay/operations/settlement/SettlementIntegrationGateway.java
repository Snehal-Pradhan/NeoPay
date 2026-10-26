package com.snehal.neopay.operations.settlement;

import com.snehal.neopay.common.dto.PaymentSettlementView;
import com.snehal.neopay.common.dto.SettlementBankDetails;
import com.snehal.neopay.common.dto.PayoutReturnLedgerRequest;
import com.snehal.neopay.common.dto.SettlementLedgerRequest;
import com.snehal.neopay.operations.client.MerchantServiceClient;
import com.snehal.neopay.operations.client.PaymentServiceClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SettlementIntegrationGateway {

    private final MerchantServiceClient merchantServiceClient;
    private final PaymentServiceClient paymentServiceClient;

    @CircuitBreaker(name = "payment-service")
    @Retry(name = "payment-service")
    public List<PaymentSettlementView> findUnsettledCaptured(UUID merchantId) {
        return paymentServiceClient.findUnsettledCaptured(merchantId);
    }

    @CircuitBreaker(name = "payment-service")
    @Retry(name = "payment-service")
    public void markSettled(List<UUID> paymentIds) {
        paymentServiceClient.markSettled(paymentIds);
    }

    @CircuitBreaker(name = "merchant-service")
    @Retry(name = "merchant-service")
    public SettlementBankDetails getSettlementBankDetails(UUID merchantId) {
        return merchantServiceClient.getSettlementBankDetails(merchantId);
    }

    @CircuitBreaker(name = "payment-service")
    @Retry(name = "payment-service")
    public void postSettlementLedger(SettlementLedgerRequest request) {
        paymentServiceClient.postSettlementLedger(request);
    }

    @CircuitBreaker(name = "payment-service")
    @Retry(name = "payment-service")
    public void postPayoutReturnLedger(PayoutReturnLedgerRequest request) {
        paymentServiceClient.postPayoutReturnLedger(request);
    }
}
