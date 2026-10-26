package com.snehal.neopay.payment.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentSimulationTest {

    @Test
    void oneMillionTransactionsHoldEveryInvariant() {
        SimulationResult result = PaymentSimulator.run(42L, 1_000_000);

        assertTrue(result.clean(), () -> "violations: " + result.violations());
        assertEquals(result.totalDebits(), result.totalCredits(),
                "total debits must equal total credits");
        assertEquals(0, result.finalMerchantPayable(),
                "merchant payable must be fully settled");
        assertTrue(result.captured() > 0);
        assertTrue(result.duplicateRequests() > 0,
                "duplicate idempotency keys must be exercised");
        assertTrue(result.refunds() > 0, "refund path must be exercised");
        assertTrue(result.settled() > 0, "settlement path must be exercised");
        assertTrue(result.failedAuthorization() > 0, "failure path must be exercised");
    }

    @Test
    void sameSeedProducesIdenticalResults() {
        SimulationResult first = PaymentSimulator.run(7L, 100_000);
        SimulationResult second = PaymentSimulator.run(7L, 100_000);

        assertEquals(first, second);
    }

    @Test
    void differentSeedsProduceDifferentResults() {
        SimulationResult first = PaymentSimulator.run(1L, 10_000);
        SimulationResult second = PaymentSimulator.run(2L, 10_000);

        assertTrue(first.totalDebits() != second.totalDebits()
                        || first.captured() != second.captured(),
                "different seeds must explore different schedules");
    }
}
