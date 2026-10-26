package com.snehal.neopay.merchant.entity;

import com.snehal.neopay.common.enums.Environment;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyTest {

    @Test
    void keyWithoutRotationIsNotInGracePeriod() {
        assertFalse(ApiKey.builder()
                .keyId("np_test_1")
                .environment(Environment.TEST)
                .build()
                .isInGracePeriod());
    }

    @Test
    void keyInsideWindowIsInGracePeriod() {
        assertTrue(ApiKey.builder()
                .keyId("np_test_1")
                .environment(Environment.TEST)
                .gracePeriodExpiresAt(LocalDateTime.now().plusMinutes(5))
                .build()
                .isInGracePeriod());
    }

    @Test
    void expiredGraceWindowIsOver() {
        assertFalse(ApiKey.builder()
                .keyId("np_test_1")
                .environment(Environment.TEST)
                .gracePeriodExpiresAt(LocalDateTime.now().minusSeconds(1))
                .build()
                .isInGracePeriod());
    }

    @Test
    void enabledDefaultsToTrue() {
        assertTrue(ApiKey.builder()
                .keyId("np_test_1")
                .environment(Environment.TEST)
                .build()
                .isEnabled());
    }
}
