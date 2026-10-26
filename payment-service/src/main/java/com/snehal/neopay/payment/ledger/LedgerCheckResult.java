package com.snehal.neopay.payment.ledger;

import java.util.List;
import java.util.UUID;

public record LedgerCheckResult(long globalDelta, List<UUID> unbalancedTransactions) {

    public boolean healthy() {
        return globalDelta == 0 && unbalancedTransactions.isEmpty();
    }
}
