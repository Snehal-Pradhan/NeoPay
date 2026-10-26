package com.snehal.neopay.vault.validation;

import org.junit.jupiter.api.Test;

import java.time.Year;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpiryYearValidatorTest {

    private final ExpiryYearValidator validator = new ExpiryYearValidator();

    @Test
    void nullYearIsInvalid() {
        assertFalse(validator.isValid(null, null));
    }

    @Test
    void currentYearIsValid() {
        assertTrue(validator.isValid(Year.now().getValue(), null));
    }

    @Test
    void futureYearIsValid() {
        assertTrue(validator.isValid(Year.now().getValue() + 5, null));
    }

    @Test
    void pastYearIsInvalid() {
        assertFalse(validator.isValid(Year.now().getValue() - 1, null));
    }
}
