package com.snehal.neopay.common.config;

import org.springframework.security.crypto.encrypt.AesBytesEncryptor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.keygen.KeyGenerators;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class AesEncryptionConfig {

    public BytesEncryptor masterKeyEncryptor(String masterKey) {
        byte[] masterKeyBytes = Base64.getDecoder().decode(masterKey);
        if (masterKeyBytes.length != 16 && masterKeyBytes.length != 24 && masterKeyBytes.length != 32) {
            throw new IllegalArgumentException("AES master key must be 128, 192 or 256 bits");
        }
        SecretKeySpec masterDecKey = new SecretKeySpec(masterKeyBytes, "AES");
        return new AesBytesEncryptor(masterDecKey, KeyGenerators.secureRandom(12),
                AesBytesEncryptor.CipherAlgorithm.GCM);
    }
}
