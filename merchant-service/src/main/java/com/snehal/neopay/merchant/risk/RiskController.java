package com.snehal.neopay.merchant.risk;

import com.snehal.neopay.common.enums.MerchantTier;

import com.snehal.neopay.common.enums.MerchantStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal")
public class RiskController {

    private final RiskService riskService;
    private final KycService kycService;

    @PostMapping("/risk/authorize")
    public RiskDecision authorize(@RequestBody AuthorizeRequest request) {
        return riskService.authorize(request.merchantId(), request.amountUnits());
    }

    @PostMapping("/kyc/{merchantId}/verify")
    public MerchantStatus verify(@PathVariable UUID merchantId) {
        return kycService.verify(merchantId);
    }

    @PostMapping("/kyc/{merchantId}/reject")
    public MerchantStatus reject(@PathVariable UUID merchantId) {
        return kycService.reject(merchantId);
    }

    @PostMapping("/kyc/{merchantId}/tier")
    public MerchantTier upgradeTier(@PathVariable UUID merchantId,
                                    @RequestBody TierRequest request) {
        return kycService.upgradeTier(merchantId, request.tier());
    }

    public record AuthorizeRequest(UUID merchantId, long amountUnits) {
    }

    public record TierRequest(MerchantTier tier) {
    }
}
