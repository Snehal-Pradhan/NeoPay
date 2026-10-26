package com.snehal.neopay.payment.statemachine;

import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.common.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentStateMachineTest {

    private final PaymentStateMachine machine = new PaymentStateMachine();

    @Test
    void allowedEdgesMatchTheDeclaredMatrix() {
        Map<PaymentStatus, Set<PaymentStatus>> expected = new EnumMap<>(PaymentStatus.class);
        expected.put(PaymentStatus.CREATED, EnumSet.of(PaymentStatus.AUTHORIZING, PaymentStatus.CANCELLED));
        expected.put(PaymentStatus.AUTHORIZING, EnumSet.of(
                PaymentStatus.AUTHORIZED, PaymentStatus.FAILED, PaymentStatus.CANCELLED));
        expected.put(PaymentStatus.AUTHORIZED, EnumSet.of(
                PaymentStatus.CAPTURING, PaymentStatus.AUTH_EXPIRED));
        expected.put(PaymentStatus.CAPTURING, EnumSet.of(
                PaymentStatus.CAPTURED, PaymentStatus.AUTHORIZED));
        expected.put(PaymentStatus.CAPTURED, EnumSet.of(
                PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED, PaymentStatus.SETTLED));
        expected.put(PaymentStatus.PARTIALLY_REFUNDED, EnumSet.of(PaymentStatus.REFUNDED, PaymentStatus.SETTLED));
        expected.put(PaymentStatus.SETTLED, EnumSet.of(PaymentStatus.PARTIALLY_REFUNDED));

        int allowedCount = 0;
        for (PaymentStatus from : PaymentStatus.values()) {
            for (PaymentStatus to : PaymentStatus.values()) {
                boolean expectedAllowed = expected.getOrDefault(from, Set.of()).contains(to);
                assertEquals(expectedAllowed, machine.canTransition(from, to),
                        "canTransition(" + from + ", " + to + ")");
                if (expectedAllowed) {
                    allowedCount++;
                }
            }
        }
        assertEquals(15, allowedCount);
        assertEquals(15, PaymentStateMachine.transitionCount());
    }

    @Test
    void terminalAndSkipStatesAreUnreachable() {
        assertFalse(machine.canTransition(PaymentStatus.CREATED, PaymentStatus.CAPTURED));
        assertFalse(machine.canTransition(PaymentStatus.CREATED, PaymentStatus.SETTLED));
        assertFalse(machine.canTransition(PaymentStatus.AUTHORIZING, PaymentStatus.CAPTURED));
        assertFalse(machine.canTransition(PaymentStatus.FAILED, PaymentStatus.CAPTURED));
        assertFalse(machine.canTransition(PaymentStatus.CANCELLED, PaymentStatus.AUTHORIZING));
        assertFalse(machine.canTransition(PaymentStatus.REFUNDED, PaymentStatus.CAPTURED));
    }

    @Test
    void selfTransitionsAreRejected() {
        for (PaymentStatus status : PaymentStatus.values()) {
            assertFalse(machine.canTransition(status, status), "self transition for " + status);
        }
    }

    @Test
    void requireTransitionThrowsOnIllegalPair() {
        assertThrows(InvalidStateTransitionException.class,
                () -> machine.requireTransition(PaymentStatus.CREATED, PaymentStatus.SETTLED));
    }

    @Test
    void requireTransitionAcceptsLegalPair() {
        machine.requireTransition(PaymentStatus.CAPTURED, PaymentStatus.SETTLED);
        assertTrue(machine.allowedTransitions(PaymentStatus.CAPTURED).contains(PaymentStatus.SETTLED));
    }

    @Test
    void nullInputsAreRejected() {
        assertFalse(machine.canTransition(null, PaymentStatus.CREATED));
        assertFalse(machine.canTransition(PaymentStatus.CREATED, null));
    }
}
