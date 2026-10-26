package com.snehal.neopay.merchant.risk;

import com.snehal.neopay.common.enums.MerchantTier;

public record RiskDecision(boolean allowed, boolean stepUpRequired, String reason) {

    public static RiskDecision permit() {
        return new RiskDecision(true, false, null);
    }

    public static RiskDecision requireStepUp() {
        return new RiskDecision(true, true, "STEP_UP_REQUIRED");
    }

    public static RiskDecision reject(String reason) {
        return new RiskDecision(false, false, reason);
    }
}
