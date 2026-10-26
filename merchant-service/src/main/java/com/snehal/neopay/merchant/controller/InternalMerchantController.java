package com.snehal.neopay.merchant.controller;

import com.snehal.neopay.common.dto.SettlementBankDetails;
import com.snehal.neopay.common.dto.WebhookTarget;
import com.snehal.neopay.merchant.api.MerchantLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/merchants")
public class InternalMerchantController {

    private final MerchantLookupService merchantLookupService;

    @GetMapping("/{merchantId}/webhook-targets")
    public List<WebhookTarget> getActiveConfigsForEvent(@PathVariable UUID merchantId,
                                                        @RequestParam String eventType) {
        return merchantLookupService.getActiveConfigsForEvent(merchantId, eventType);
    }

    @GetMapping("/active-ids")
    public List<UUID> listActiveMerchantIds() {
        return merchantLookupService.listActiveMerchantIds();
    }

    @GetMapping("/{merchantId}/settlement-bank-details")
    public SettlementBankDetails getSettlementBankDetails(@PathVariable UUID merchantId) {
        return merchantLookupService.getSettlementBankDetails(merchantId);
    }
}
