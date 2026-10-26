package com.snehal.neopay.payment.ledger;

import java.util.UUID;

public record LedgerReference(String type, UUID id) {

    public static final String PAYMENT = "PAYMENT";
    public static final String SETTLEMENT = "SETTLEMENT";
    public static final String REFUND = "REFUND";
    public static final String RESERVE_RELEASE = "RESERVE_RELEASE";
    public static final String DISPUTE = "DISPUTE";
    public static final String PAYOUT_RETURN = "PAYOUT_RETURN";

    public LedgerReference {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Reference type is required");
        }
        if (id == null) {
            throw new IllegalArgumentException("Reference id is required");
        }
    }

    public static LedgerReference payment(UUID paymentId) {
        return new LedgerReference(PAYMENT, paymentId);
    }

    public static LedgerReference settlement(UUID settlementId) {
        return new LedgerReference(SETTLEMENT, settlementId);
    }

    public static LedgerReference refund(UUID refundId) {
        return new LedgerReference(REFUND, refundId);
    }

    public static LedgerReference reserveRelease(UUID settlementId) {
        return new LedgerReference(RESERVE_RELEASE, settlementId);
    }

    public static LedgerReference dispute(UUID disputeId) {
        return new LedgerReference(DISPUTE, disputeId);
    }

    public static LedgerReference payoutReturn(UUID payoutId) {
        return new LedgerReference(PAYOUT_RETURN, payoutId);
    }
}
