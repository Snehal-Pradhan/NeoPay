package com.snehal.neopay.operations.settlement;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class SettlementSimulatorGuardTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void settlementSimulatorIsOptIn() {
        assertEquals(0, context.getBeanNamesForType(BankSettlementCallbackSimulator.class).length,
                "the settlement simulator must not run unless explicitly enabled");
    }
}
