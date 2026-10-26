package com.snehal.neopay.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SignerUtilTest {

    private final SignerUtil signer = new SignerUtil();

    @Test
    void signatureIsDeterministicHex() {
        String first = signer.sign("{\"a\":1}", "secret");
        String second = signer.sign("{\"a\":1}", "secret");

        assertEquals(first, second);
        assertEquals(64, first.length());
        assertEquals(first.toLowerCase(), first);
    }

    @Test
    void differentSecretProducesDifferentSignature() {
        assertNotEquals(signer.sign("payload", "one"), signer.sign("payload", "two"));
    }

    @Test
    void differentPayloadProducesDifferentSignature() {
        assertNotEquals(signer.sign("one", "secret"), signer.sign("two", "secret"));
    }

    @Test
    void matchesKnownHmacSha256Vector() {
        // RFC 4231 test case 2
        assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
                signer.sign("what do ya want for nothing?", "Jefe"));
    }
}
