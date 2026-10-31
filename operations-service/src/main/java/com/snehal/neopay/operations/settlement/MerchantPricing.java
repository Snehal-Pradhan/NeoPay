package com.snehal.neopay.operations.settlement;

import com.snehal.neopay.common.money.PricingTerms;

import java.util.Map;

/**
 * Resolved pricing for one merchant on one date: a default rule plus optional per-method
 * overrides. A missing method key means the default applies.
 */
public record MerchantPricing(PricingTerms defaultTerms, Map<String, PricingTerms> byMethod) {

    public static MerchantPricing standard() {
        return new MerchantPricing(PricingTerms.standardIndia(), Map.of());
    }

    public PricingTerms termsFor(String paymentMethod) {
        if (paymentMethod == null) {
            return defaultTerms;
        }
        return byMethod.getOrDefault(paymentMethod, defaultTerms);
    }
}
