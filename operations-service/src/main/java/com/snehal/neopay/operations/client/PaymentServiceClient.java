package com.snehal.neopay.operations.client;

import com.snehal.neopay.common.dto.PaymentSettlementView;
import com.snehal.neopay.common.dto.PayoutReturnLedgerRequest;
import com.snehal.neopay.common.dto.SettlementLedgerRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "payment-service", path = "/internal/payments", url = "${PAYMENT_SERVICE_URI:}")
public interface PaymentServiceClient {

    @GetMapping("/unsettled-captured")
    List<PaymentSettlementView> findUnsettledCaptured(@RequestParam UUID merchantId);

    @PostMapping("/mark-settled")
    void markSettled(@RequestBody List<UUID> paymentIds);


    @PostMapping("/internal/ledger/settlement")
    void postSettlementLedger(@RequestBody SettlementLedgerRequest request);

    @PostMapping("/internal/ledger/payout-return")
    void postPayoutReturnLedger(@RequestBody PayoutReturnLedgerRequest request);
}
