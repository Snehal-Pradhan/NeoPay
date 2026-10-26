package com.snehal.neopay.merchant.risk;

import com.snehal.neopay.common.enums.MerchantTier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguredSanctionsScreenerTest {

    @Test
    void matchesConfiguredNamesIgnoringCase() {
        SanctionsScreener screener = new ConfiguredSanctionsScreener("Blocked Corp, Acme Shell");

        assertTrue(screener.isSanctioned("blocked corp"));
        assertTrue(screener.isSanctioned("ACME SHELL TRADING"));
        assertFalse(screener.isSanctioned("Legit Merchant"));
    }

    @Test
    void emptyConfigurationMatchesNothing() {
        SanctionsScreener screener = new ConfiguredSanctionsScreener("");

        assertFalse(screener.isSanctioned("Anyone"));
        assertFalse(screener.isSanctioned(null));
    }
}
