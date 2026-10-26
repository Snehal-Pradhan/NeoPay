package com.snehal.neopay.merchant.risk;

import com.snehal.neopay.common.enums.MerchantTier;

import com.snehal.neopay.common.enums.MerchantStatus;
import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.merchant.entity.Merchant;
import com.snehal.neopay.merchant.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RiskService {

    private final MerchantRepository merchantRepository;
    private final MerchantLimitUsageRepository usageRepository;
    private final SanctionsScreener sanctionsScreener;

    @Transactional
    public RiskDecision authorize(UUID merchantId, long amountUnits) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", merchantId));

        if (merchant.getStatus() != MerchantStatus.ACTIVE) {
            return RiskDecision.reject("KYC_NOT_VERIFIED");
        }
        if (amountUnits <= 0) {
            return RiskDecision.reject("INVALID_AMOUNT");
        }
        if (sanctionsScreener.isSanctioned(merchant.getName())) {
            return RiskDecision.reject("SANCTIONS_HIT");
        }

        MerchantTier tier = merchant.getTier() == null ? MerchantTier.TIER_1 : merchant.getTier();
        if (amountUnits > tier.maxPerTransaction()) {
            return RiskDecision.reject("LIMIT_EXCEEDED");
        }

        LocalDate today = LocalDate.now();
        MerchantLimitUsage usage = usageRepository
                .findByMerchantIdAndUsageDate(merchantId, today)
                .orElseGet(() -> MerchantLimitUsage.builder()
                        .merchantId(merchantId)
                        .usageDate(today)
                        .amountUnits(0)
                        .requestCount(0)
                        .build());

        if (usage.getAmountUnits() + amountUnits > tier.dailyLimit()) {
            return RiskDecision.reject("DAILY_LIMIT_EXCEEDED");
        }

        boolean stepUp = usage.getRequestCount() >= tier.velocityThreshold();
        usage.add(amountUnits);
        usageRepository.save(usage);
        return stepUp ? RiskDecision.requireStepUp() : RiskDecision.permit();
    }
}
