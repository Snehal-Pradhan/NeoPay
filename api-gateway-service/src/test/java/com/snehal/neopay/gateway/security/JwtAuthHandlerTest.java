package com.snehal.neopay.gateway.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthHandlerTest {

    private final JwtVerifier verifier = mock(JwtVerifier.class);
    private final JwtAuthHandler handler = new JwtAuthHandler(verifier);
    private final Claims claims = mock(Claims.class);

    @Test
    void validTokenProducesIdentityHeaders() {
        when(verifier.verify("token")).thenReturn(claims);
        when(verifier.extractMerchantId(claims)).thenReturn("merchant-1");
        when(verifier.extractRole(claims)).thenReturn("OWNER");

        Map<String, String> headers = handler.authenticate("token");

        assertEquals("merchant-1", headers.get("X-Merchant-Id"));
        assertEquals("OWNER", headers.get("X-User-Role"));
    }

    @Test
    void missingMerchantClaimIsRejected() {
        when(verifier.verify("token")).thenReturn(claims);
        when(verifier.extractMerchantId(claims)).thenReturn(null);
        when(verifier.extractRole(claims)).thenReturn("OWNER");

        assertThrows(GatewayAuthenticationException.class, () -> handler.authenticate("token"));
    }

    @Test
    void missingRoleClaimIsRejected() {
        when(verifier.verify("token")).thenReturn(claims);
        when(verifier.extractMerchantId(claims)).thenReturn("merchant-1");
        when(verifier.extractRole(claims)).thenReturn(" ");

        assertThrows(GatewayAuthenticationException.class, () -> handler.authenticate("token"));
    }

    @Test
    void invalidTokenIsRejected() {
        when(verifier.verify("bad")).thenThrow(new IllegalStateException("signature"));

        assertThrows(GatewayAuthenticationException.class, () -> handler.authenticate("bad"));
    }
}
