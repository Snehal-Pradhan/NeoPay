package com.snehal.neopay.common.enums;

/**
 * Tier limits are expressed in minor units of the settlement currency.
 */
public enum MerchantTier {

    TIER_1(100_000, 500_000, 5),
    TIER_2(1_000_000, 10_000_000, 20),
    TIER_3(10_000_000, 100_000_000, 100);

    private final long maxPerTransaction;
    private final long dailyLimit;
    private final int velocityThreshold;

    MerchantTier(long maxPerTransaction, long dailyLimit, int velocityThreshold) {
        this.maxPerTransaction = maxPerTransaction;
        this.dailyLimit = dailyLimit;
        this.velocityThreshold = velocityThreshold;
    }

    public long maxPerTransaction() {
        return maxPerTransaction;
    }

    public long dailyLimit() {
        return dailyLimit;
    }

    public int velocityThreshold() {
        return velocityThreshold;
    }
}
