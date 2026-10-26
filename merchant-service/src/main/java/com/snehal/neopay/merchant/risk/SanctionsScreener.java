package com.snehal.neopay.merchant.risk;

import com.snehal.neopay.common.enums.MerchantTier;

public interface SanctionsScreener {

    boolean isSanctioned(String name);
}
