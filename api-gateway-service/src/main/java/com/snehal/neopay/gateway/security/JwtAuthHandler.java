package com.snehal.neopay.gateway.security;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtAuthHandler {

    private final JwtVerifier jwtVerifier;

    public Map<String, String> authenticate(String token) {
        Claims claims;
        try {
            claims = jwtVerifier.verify(token);
        } catch (Exception e) {
            throw new GatewayAuthenticationException("Invalid or expired token");
        }

        String merchantId = jwtVerifier.extractMerchantId(claims);
        String role = jwtVerifier.extractRole(claims);
        if (merchantId == null || merchantId.isBlank() || role == null || role.isBlank()) {
            throw new GatewayAuthenticationException("Token is missing required claims");
        }

        return Map.of(
                "X-Merchant-Id", merchantId,
                "X-User-Role", role
        );
    }
}
