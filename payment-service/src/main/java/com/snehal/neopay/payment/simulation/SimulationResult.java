package com.snehal.neopay.payment.simulation;

import java.util.List;

public record SimulationResult(
        long seed,
        int transactions,
        long captured,
        long failedAuthorization,
        long duplicateRequests,
        long refunds,
        long settled,
        long totalDebits,
        long totalCredits,
        long finalMerchantPayable,
        List<String> violations
) {
    public boolean clean() {
        return violations.isEmpty();
    }
}
