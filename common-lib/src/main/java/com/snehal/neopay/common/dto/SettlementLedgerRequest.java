package com.snehal.neopay.common.dto;

import java.util.UUID;

/**
 * Settlement posting request. Amounts are in minor units of one currency.
 * Construction rejects negative components and any set that does not sum to gross.
 */
public record SettlementLedgerRequest(
        UUID settlementId,
        long grossUnits,
        long feeUnits,
        long gstUnits,
        long tdsUnits,
        long reserveUnits,
        long netUnits,
        String currency
) {

    public SettlementLedgerRequest {
        if (feeUnits < 0 || gstUnits < 0 || tdsUnits < 0 || reserveUnits < 0 || netUnits < 0) {
            throw new IllegalArgumentException("Settlement components must be non-negative");
        }
        long sum = Math.addExact(Math.addExact(Math.addExact(feeUnits, gstUnits), tdsUnits),
                Math.addExact(reserveUnits, netUnits));
        if (sum != grossUnits) {
            throw new IllegalArgumentException(
                    "Unbalanced settlement: gross=" + grossUnits + " components=" + sum);
        }
    }
}
