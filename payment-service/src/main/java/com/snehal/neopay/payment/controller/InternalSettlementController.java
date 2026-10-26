package com.snehal.neopay.payment.controller;

import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.common.domain.PaymentId;
import com.snehal.neopay.common.dto.PaymentSettlementView;
import com.snehal.neopay.payment.api.PaymentLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/payments")
public class InternalSettlementController {

    private final PaymentLookupService settlementLookupService;

    @GetMapping("/unsettled-captured")
    public List<PaymentSettlementView> findUnsettledCaptured(@RequestParam UUID merchantId) {
        return settlementLookupService.findUnsettledCapturedPayments(MerchantId.of(merchantId));
    }

    @PostMapping("/mark-settled")
    public void markSettled(@RequestBody List<UUID> paymentIds) {
        settlementLookupService.markSettled(paymentIds.stream().map(PaymentId::of).toList());
    }
}
