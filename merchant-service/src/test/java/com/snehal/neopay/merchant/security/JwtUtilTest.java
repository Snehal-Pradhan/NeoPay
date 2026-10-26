package com.snehal.neopay.merchant.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKey", SECRET);
    }

    @Test
    void roundTripsSubjectMerchantAndRole() {
        UUID merchantId = UUID.randomUUID();

        String token = jwtUtil.generateAccessToken("owner@example.com", merchantId, "OWNER");
        Claims claims = jwtUtil.verifyAccessToken(token);

        assertEquals("owner@example.com", claims.getSubject());
        assertEquals(merchantId, UUID.fromString(jwtUtil.extractMerchantId(claims)));
        assertEquals("OWNER", jwtUtil.extractRole(claims));
    }

    @Test
    void tokenExpiresInTheFuture() {
        String token = jwtUtil.generateAccessToken("owner@example.com", UUID.randomUUID(), "OWNER");

        Claims claims = jwtUtil.verifyAccessToken(token);

        assertTrue(claims.getExpiration().toInstant().isAfter(claims.getIssuedAt().toInstant()));
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtUtil.generateAccessToken("owner@example.com", UUID.randomUUID(), "OWNER");
        String tampered = token.substring(0, token.length() - 2) + "aa";

        assertThrows(JwtException.class, () -> jwtUtil.verifyAccessToken(tampered));
    }

    @Test
    void tokenFromAnotherSecretIsRejected() {
        String token = jwtUtil.generateAccessToken("owner@example.com", UUID.randomUUID(), "OWNER");

        JwtUtil other = new JwtUtil();
        ReflectionTestUtils.setField(other, "secretKey", "ffffffffffffffffffffffffffffffff");

        assertThrows(JwtException.class, () -> other.verifyAccessToken(token));
    }
}
