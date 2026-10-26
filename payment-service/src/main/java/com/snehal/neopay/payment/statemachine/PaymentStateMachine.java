package com.snehal.neopay.payment.statemachine;

import com.snehal.neopay.common.enums.PaymentEvent;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.common.exception.InvalidStateTransitionException;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class PaymentStateMachine {

    private record Transition(PaymentStatus from, PaymentEvent event) {
    }

    private static final Map<Transition, PaymentStatus> TRANSITION = Map.ofEntries(
            Map.entry(new Transition(PaymentStatus.CREATED, PaymentEvent.AUTHORIZE_ATTEMPT), PaymentStatus.AUTHORIZING),
            Map.entry(new Transition(PaymentStatus.AUTHORIZING, PaymentEvent.AUTHORIZE_SUCCESS), PaymentStatus.AUTHORIZED),
            Map.entry(new Transition(PaymentStatus.AUTHORIZING, PaymentEvent.AUTHORIZE_FAIL), PaymentStatus.FAILED),
            Map.entry(new Transition(PaymentStatus.AUTHORIZED, PaymentEvent.CAPTURE_REQUEST), PaymentStatus.CAPTURING),
            Map.entry(new Transition(PaymentStatus.CAPTURING, PaymentEvent.CAPTURE_SUCCESS), PaymentStatus.CAPTURED),
            Map.entry(new Transition(PaymentStatus.CAPTURING, PaymentEvent.CAPTURE_FAIL), PaymentStatus.AUTHORIZED),
            Map.entry(new Transition(PaymentStatus.CAPTURED, PaymentEvent.REFUND_INIT), PaymentStatus.PARTIALLY_REFUNDED),
            Map.entry(new Transition(PaymentStatus.PARTIALLY_REFUNDED, PaymentEvent.REFUND_COMPLETE), PaymentStatus.REFUNDED),
            Map.entry(new Transition(PaymentStatus.CAPTURED, PaymentEvent.REFUND_COMPLETE), PaymentStatus.REFUNDED),
            Map.entry(new Transition(PaymentStatus.CAPTURED, PaymentEvent.SETTLE), PaymentStatus.SETTLED),
            Map.entry(new Transition(PaymentStatus.SETTLED, PaymentEvent.REFUND_INIT), PaymentStatus.PARTIALLY_REFUNDED),
            Map.entry(new Transition(PaymentStatus.PARTIALLY_REFUNDED, PaymentEvent.SETTLE), PaymentStatus.SETTLED),

            Map.entry(new Transition(PaymentStatus.CREATED, PaymentEvent.CANCEL), PaymentStatus.CANCELLED),
            Map.entry(new Transition(PaymentStatus.AUTHORIZING, PaymentEvent.CANCEL), PaymentStatus.CANCELLED),
            Map.entry(new Transition(PaymentStatus.AUTHORIZED, PaymentEvent.CAPTURE_TIMEOUT), PaymentStatus.AUTH_EXPIRED)
    );

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = buildAllowed();

    private static Map<PaymentStatus, Set<PaymentStatus>> buildAllowed() {
        Map<PaymentStatus, EnumSet<PaymentStatus>> mutable = new EnumMap<>(PaymentStatus.class);
        for (Map.Entry<Transition, PaymentStatus> entry : TRANSITION.entrySet()) {
            mutable.computeIfAbsent(entry.getKey().from(), ignored -> EnumSet.noneOf(PaymentStatus.class))
                    .add(entry.getValue());
        }
        Map<PaymentStatus, Set<PaymentStatus>> frozen = new EnumMap<>(PaymentStatus.class);
        mutable.forEach((from, to) -> frozen.put(from, Collections.unmodifiableSet(to)));
        return Collections.unmodifiableMap(frozen);
    }

    public PaymentStatus transition(PaymentStatus current, PaymentEvent event) {
        PaymentStatus next = TRANSITION.get(new Transition(current, event));
        if (next == null) {
            throw new InvalidStateTransitionException(current.name(), event.name());
        }
        return next;
    }

    public boolean canTransition(PaymentStatus from, PaymentStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public void requireTransition(PaymentStatus from, PaymentStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidStateTransitionException(from.name(), to.name());
        }
    }

    public Set<PaymentStatus> allowedTransitions(PaymentStatus from) {
        return ALLOWED.getOrDefault(from, Set.of());
    }

    public static int transitionCount() {
        return TRANSITION.size();
    }
}
