package com.snehal.neopay.payment.simulation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Double-entry ledger used by the deterministic simulator.
 * Every posting moves value between two accounts; total debits must equal
 * total credits.
 */
public final class Ledger {

    private final Map<String, Long> debits = new LinkedHashMap<>();
    private final Map<String, Long> credits = new LinkedHashMap<>();
    private long totalDebits;
    private long totalCredits;

    public void post(String debitAccount, String creditAccount, long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Posting amount must be non-negative");
        }
        debits.merge(debitAccount, amount, Math::addExact);
        credits.merge(creditAccount, amount, Math::addExact);
        totalDebits = Math.addExact(totalDebits, amount);
        totalCredits = Math.addExact(totalCredits, amount);
    }

    public boolean balanced() {
        return totalDebits == totalCredits;
    }

    /**
     * Credits minus debits. Zero means the account holds no residual value.
     */
    public long balance(String account) {
        return creditOf(account) - debitOf(account);
    }

    public long totalDebits() {
        return totalDebits;
    }

    public long totalCredits() {
        return totalCredits;
    }

    public long debitOf(String account) {
        return debits.getOrDefault(account, 0L);
    }

    public long creditOf(String account) {
        return credits.getOrDefault(account, 0L);
    }
}
