package com.snehal.neopay.payment.formal;

import com.snehal.neopay.common.enums.PaymentEvent;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.payment.statemachine.PaymentStateMachine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Trace conformance: every edge in the TLA+ model must be accepted by the production
 * state machine with the same result. The model is intentionally a subset of the
 * implementation; extra implementation transitions are allowed, missing or divergent
 * ones are not.
 */
class ModelConformanceTest {

    private final PaymentStateMachine machine = new PaymentStateMachine();

    @Test
    void authorizeEdgeMatches() {
        assertEdge(PaymentStatus.CREATED, PaymentEvent.AUTHORIZE_ATTEMPT,
                PaymentStatus.AUTHORIZING);
    }

    @Test
    void approveEdgeMatches() {
        assertEdge(PaymentStatus.AUTHORIZING, PaymentEvent.AUTHORIZE_SUCCESS,
                PaymentStatus.AUTHORIZED);
    }

    @Test
    void failEdgeMatches() {
        assertEdge(PaymentStatus.AUTHORIZING, PaymentEvent.AUTHORIZE_FAIL, PaymentStatus.FAILED);
    }

    @Test
    void captureRequestEdgeMatches() {
        assertEdge(PaymentStatus.AUTHORIZED, PaymentEvent.CAPTURE_REQUEST,
                PaymentStatus.CAPTURING);
    }

    @Test
    void captureEdgeMatches() {
        assertEdge(PaymentStatus.CAPTURING, PaymentEvent.CAPTURE_SUCCESS,
                PaymentStatus.CAPTURED);
    }

    @Test
    void settleEdgeMatches() {
        assertEdge(PaymentStatus.CAPTURED, PaymentEvent.SETTLE, PaymentStatus.SETTLED);
    }

    @Test
    void partialRefundEdgeMatches() {
        assertEdge(PaymentStatus.CAPTURED, PaymentEvent.REFUND_INIT,
                PaymentStatus.PARTIALLY_REFUNDED);
        assertEdge(PaymentStatus.SETTLED, PaymentEvent.REFUND_INIT,
                PaymentStatus.PARTIALLY_REFUNDED);
    }

    @Test
    void fullRefundEdgeMatches() {
        assertEdge(PaymentStatus.PARTIALLY_REFUNDED, PaymentEvent.REFUND_COMPLETE,
                PaymentStatus.REFUNDED);
        assertEdge(PaymentStatus.CAPTURED, PaymentEvent.REFUND_COMPLETE,
                PaymentStatus.REFUNDED);
    }

    private void assertEdge(PaymentStatus from, PaymentEvent event, PaymentStatus expected) {
        assertEquals(expected, machine.transition(from, event),
                "model edge " + from + " --" + event + "--> " + expected);
    }
}
