package com.snehal.neopay.payment.simulator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class SimulatorProductionGuardTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void simulatorIsNotActiveUnlessExplicitlyEnabled() {
        assertEquals(0, context.getBeanNamesForType(BankCallbackSimulator.class).length,
                "the bank-callback simulator must be opt-in");
    }
}
