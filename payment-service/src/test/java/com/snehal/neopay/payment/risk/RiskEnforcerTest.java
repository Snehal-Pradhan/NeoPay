package com.snehal.neopay.payment.risk;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.exception.RiskRejectedException;
import com.snehal.neopay.common.exception.StepUpRequiredException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskEnforcerTest {

    private static final UUID MERCHANT = UUID.randomUUID();

    private RiskClient client;
    private SimpleMeterRegistry registry;
    private RiskEnforcer enforcer;

    @BeforeEach
    void setUp() {
        client = mock(RiskClient.class);
        registry = new SimpleMeterRegistry();
        enforcer = new RiskEnforcer(client, registry);
    }

    @Test
    void disabledEnforcementNeverCallsTheClient() {
        enforcer.enforce(MERCHANT, Money.inr(1_000));

        verify(client, never()).authorize(any());
    }

    @Test
    void allowedDecisionPasses() {
        enabled(true);
        when(client.authorize(any())).thenReturn(new RiskDecisionResponse(true, false, null));

        enforcer.enforce(MERCHANT, Money.inr(1_000));
    }

    @Test
    void rejectedDecisionThrowsWithReason() {
        enabled(true);
        when(client.authorize(any())).thenReturn(new RiskDecisionResponse(false, false, "LIMIT_EXCEEDED"));

        RiskRejectedException exception = assertThrows(RiskRejectedException.class,
                () -> enforcer.enforce(MERCHANT, Money.inr(1_000)));
        assertEquals("LIMIT_EXCEEDED", exception.getReason());
    }

    @Test
    void stepUpDecisionThrows() {
        enabled(true);
        when(client.authorize(any())).thenReturn(new RiskDecisionResponse(true, true, "STEP_UP_REQUIRED"));

        assertThrows(StepUpRequiredException.class,
                () -> enforcer.enforce(MERCHANT, Money.inr(1_000)));
    }

    @Test
    void unavailableClientFailsOpenAndCounts() {
        enabled(true);
        when(client.authorize(any())).thenThrow(new IllegalStateException("merchant service down"));

        enforcer.enforce(MERCHANT, Money.inr(1_000));

        assertEquals(1.0, registry.get("risk_unavailable_total").counter().count());
    }

    private void enabled(boolean value) {
        ReflectionTestUtils.setField(enforcer, "enforcementEnabled", value);
    }
}
