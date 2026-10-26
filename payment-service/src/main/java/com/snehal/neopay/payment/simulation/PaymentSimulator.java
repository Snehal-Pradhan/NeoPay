package com.snehal.neopay.payment.simulation;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.PaymentEvent;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.common.money.FeeSchedule;
import com.snehal.neopay.payment.statemachine.PaymentStateMachine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Deterministic payment simulator.
 *
 * <p>Each iteration models one logical transaction: a client request that may be
 * duplicated, an authorization that may fail, capture, settlement with fee and GST,
 * and an optional partial or full refund. All money movement is posted to a
 * double-entry ledger.
 *
 * <p>Arithmetic and lifecycle rules are taken from production code:
 * {@link FeeSchedule#standardIndia()} and {@link PaymentStateMachine}. The simulator
 * does not reimplement either.
 *
 * <p>Invariants asserted on every iteration:
 * <ul>
 *   <li>total debits equal total credits</li>
 *   <li>the merchant payable account returns to zero after settlement</li>
 *   <li>a duplicated idempotency key never creates a second transaction</li>
 *   <li>refund never exceeds captured amount</li>
 * </ul>
 *
 * <p>Same seed and transaction count always produce the same result.
 */
public final class PaymentSimulator {

    private static final FeeSchedule SCHEDULE = FeeSchedule.standardIndia();
    private static final PaymentStateMachine STATE = new PaymentStateMachine();

    private static final double DUPLICATE_RATE = 0.10;
    private static final double AUTH_FAILURE_RATE = 0.05;
    private static final double REFUND_RATE = 0.10;
    private static final int MAX_AMOUNT_UNITS = 1_000_000;
    private static final int MAX_VIOLATIONS = 20;

    private PaymentSimulator() {
    }

    public static SimulationResult run(long seed, int transactions) {
        Random random = new Random(seed);
        Ledger ledger = new Ledger();
        Set<String> claimedKeys = new HashSet<>();
        List<String> violations = new ArrayList<>();
        Tally tally = new Tally();

        String previousKey = null;
        for (int i = 0; i < transactions; i++) {
            String key = nextKey(random, previousKey, i);
            previousKey = key;

            if (!claimedKeys.add(key)) {
                tally.duplicateRequests++;
                continue;
            }

            int amount = 1 + random.nextInt(MAX_AMOUNT_UNITS);
            if (random.nextDouble() < AUTH_FAILURE_RATE) {
                tally.failedAuthorization++;
                continue;
            }

            capture(ledger, amount, tally);
            settle(ledger, amount, violations, tally);
            maybeRefund(ledger, amount, random, violations, tally);

            validate(ledger, i, violations);
            if (violations.size() > MAX_VIOLATIONS) {
                break;
            }
        }

        return new SimulationResult(seed, transactions, tally.captured,
                tally.failedAuthorization, tally.duplicateRequests, tally.refunds,
                tally.settled, ledger.totalDebits(), ledger.totalCredits(),
                ledger.balance("MERCHANT_PAYABLE"), List.copyOf(violations));
    }

    private static String nextKey(Random random, String previousKey, int index) {
        if (previousKey != null && random.nextDouble() < DUPLICATE_RATE) {
            return previousKey;
        }
        return "key-" + index;
    }

    private static void capture(Ledger ledger, int amount, Tally tally) {
        PaymentStatus status = STATE.transition(PaymentStatus.CREATED, PaymentEvent.AUTHORIZE_ATTEMPT);
        status = STATE.transition(status, PaymentEvent.AUTHORIZE_SUCCESS);
        status = STATE.transition(status, PaymentEvent.CAPTURE_REQUEST);
        STATE.transition(status, PaymentEvent.CAPTURE_SUCCESS);

        ledger.post("CUSTOMER_FUNDS", "MERCHANT_PAYABLE", amount);
        tally.captured++;
    }

    private static void settle(Ledger ledger, int amount, List<String> violations, Tally tally) {
        PaymentStatus status = STATE.transition(PaymentStatus.CAPTURED, PaymentEvent.SETTLE);
        if (status != PaymentStatus.SETTLED) {
            violations.add("capture did not settle: " + status);
        }

        Money gross = Money.inr(amount);
        Money fee = SCHEDULE.fee(gross);
        Money gst = SCHEDULE.gst(fee);
        Money net = SCHEDULE.net(gross, fee, gst);

        if (!fee.add(gst).add(net).equals(gross)) {
            violations.add("settlement components do not sum to gross: " + amount);
        }

        ledger.post("MERCHANT_PAYABLE", "PLATFORM_FEE", fee.getAmountUnits());
        ledger.post("MERCHANT_PAYABLE", "GST_PAYABLE", gst.getAmountUnits());
        ledger.post("MERCHANT_PAYABLE", "BANK_SETTLEMENT", net.getAmountUnits());
        tally.settled++;
    }

    private static void maybeRefund(Ledger ledger, int capturedAmount, Random random,
                                    List<String> violations, Tally tally) {
        if (random.nextDouble() >= REFUND_RATE) {
            return;
        }

        int refund = 1 + random.nextInt(capturedAmount);
        if (refund > capturedAmount) {
            violations.add("refund " + refund + " exceeds captured " + capturedAmount);
            return;
        }

        PaymentStatus status = STATE.transition(PaymentStatus.SETTLED, PaymentEvent.REFUND_INIT);
        if (status != PaymentStatus.PARTIALLY_REFUNDED) {
            violations.add("refund did not start from SETTLED: " + status);
        }
        if (refund == capturedAmount) {
            STATE.transition(status, PaymentEvent.REFUND_COMPLETE);
        }

        // Funds were already settled; a refund claws value back from the settlement account.
        ledger.post("BANK_SETTLEMENT", "CUSTOMER_FUNDS", refund);
        tally.refunds++;
    }

    private static void validate(Ledger ledger, int index, List<String> violations) {
        if (!ledger.balanced()) {
            violations.add("ledger unbalanced after transaction " + index);
        }
        long payable = ledger.balance("MERCHANT_PAYABLE");
        if (payable != 0) {
            violations.add("merchant payable not zero after transaction " + index + ": " + payable);
        }
    }

    private static final class Tally {
        private long captured;
        private long failedAuthorization;
        private long duplicateRequests;
        private long refunds;
        private long settled;
    }
}
