package com.snehal.neopay.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.encrypt.BytesEncryptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AesEncryptionConfigTest {

    private static final String KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Test
    void encryptDecryptRoundTrip() {
        BytesEncryptor encryptor = new AesEncryptionConfig().masterKeyEncryptor(KEY);
        byte[] plaintext = "4111111111111111".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = encryptor.encrypt(plaintext);

        assertFalse(new String(ciphertext, StandardCharsets.UTF_8).contains("4111"));
        assertArrayEquals(plaintext, encryptor.decrypt(ciphertext));
    }

    @Test
    void invalidKeyIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new AesEncryptionConfig().masterKeyEncryptor("not-base64"));
    }
}
