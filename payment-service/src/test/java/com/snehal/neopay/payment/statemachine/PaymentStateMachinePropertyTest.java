package com.snehal.neopay.payment.statemachine;

import com.snehal.neopay.common.enums.PaymentEvent;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.common.exception.InvalidStateTransitionException;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentStateMachinePropertyTest {

    private final PaymentStateMachine machine = new PaymentStateMachine();

    @Provide
    Arbitrary<List<PaymentEvent>> eventSequences() {
        return Arbitraries.of(PaymentEvent.values()).list().ofMaxSize(30);
    }

    @Property(tries = 2_000)
    void settlesOnlyAfterCapture(@ForAll("eventSequences") List<PaymentEvent> events) {
        PaymentStatus status = PaymentStatus.CREATED;
        boolean captured = false;

        for (PaymentEvent event : events) {
            PaymentStatus next;
            try {
                next = machine.transition(status, event);
            } catch (InvalidStateTransitionException rejected) {
                continue;
            }
            if (next == PaymentStatus.SETTLED) {
                assertTrue(captured, "SETTLED reached without CAPTURED; path=" + events);
            }
            if (next == PaymentStatus.CAPTURED) {
                captured = true;
            }
            status = next;
        }
    }

    @Property(tries = 2_000)
    void captureOnlyAfterAuthorization(@ForAll("eventSequences") List<PaymentEvent> events) {
        PaymentStatus status = PaymentStatus.CREATED;
        boolean authorized = false;

        for (PaymentEvent event : events) {
            PaymentStatus next;
            try {
                next = machine.transition(status, event);
            } catch (InvalidStateTransitionException rejected) {
                continue;
            }
            if (next == PaymentStatus.CAPTURED) {
                assertTrue(authorized, "CAPTURED reached without AUTHORIZED; path=" + events);
            }
            if (next == PaymentStatus.AUTHORIZED) {
                authorized = true;
            }
            status = next;
        }
    }

    @Property(tries = 2_000)
    void settledIsNotTerminalForRefunds(@ForAll("eventSequences") List<PaymentEvent> events) {
        PaymentStatus status = PaymentStatus.CREATED;

        for (PaymentEvent event : events) {
            try {
                status = machine.transition(status, event);
            } catch (InvalidStateTransitionException rejected) {
                // rejected transitions leave state unchanged
            }
            if (status == PaymentStatus.SETTLED) {
                assertTrue(machine.canTransition(PaymentStatus.SETTLED, PaymentStatus.PARTIALLY_REFUNDED));
            }
        }
    }
}
