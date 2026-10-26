package com.snehal.neopay.merchant.risk;

import com.snehal.neopay.common.enums.MerchantTier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface MerchantLimitUsageRepository extends JpaRepository<MerchantLimitUsage, UUID> {

    Optional<MerchantLimitUsage> findByMerchantIdAndUsageDate(UUID merchantId, LocalDate usageDate);
}
