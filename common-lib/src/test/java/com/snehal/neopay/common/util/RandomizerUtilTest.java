package com.snehal.neopay.common.util;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RandomizerUtilTest {

    @Test
    void encodesTheRequestedNumberOfRandomBytes() {
        String value = RandomizerUtil.randomBase64(16);

        assertEquals(16, Base64.getUrlDecoder().decode(value).length);
    }

    @Test
    void outputIsUrlSafeWithoutPadding() {
        String value = RandomizerUtil.randomBase64(48);

        assertEquals(value, value.replace("+", "").replace("/", "").replace("=", ""));
        assertEquals(64, value.length());
    }

    @Test
    void successiveCallsDiffer() {
        assertNotEquals(RandomizerUtil.randomBase64(32), RandomizerUtil.randomBase64(32));
    }

    @Test
    void zeroLengthProducesEmptyString() {
        assertEquals("", RandomizerUtil.randomBase64(0));
    }
}
