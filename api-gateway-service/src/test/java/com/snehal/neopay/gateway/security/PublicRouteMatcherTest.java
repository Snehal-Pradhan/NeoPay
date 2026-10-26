package com.snehal.neopay.gateway.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicRouteMatcherTest {

    @Test
    void matchesConfiguredPatterns() {
        SecurityRouteProperties properties = new SecurityRouteProperties();
        properties.setPublicRoutes(List.of("/v1/auth/**", "/webhook/**", "/actuator/**"));
        PublicRouteMatcher matcher = new PublicRouteMatcher(properties);

        assertTrue(matcher.isPublic("/v1/auth/login"));
        assertTrue(matcher.isPublic("/v1/auth/signup"));
        assertTrue(matcher.isPublic("/webhook/success"));
        assertTrue(matcher.isPublic("/actuator/health"));
    }

    @Test
    void protectsOtherRoutes() {
        SecurityRouteProperties properties = new SecurityRouteProperties();
        properties.setPublicRoutes(List.of("/v1/auth/**"));
        PublicRouteMatcher matcher = new PublicRouteMatcher(properties);

        assertFalse(matcher.isPublic("/v1/payments"));
        assertFalse(matcher.isPublic("/v1/merchants/api-keys"));
    }

    @Test
    void emptyListProtectsEverything() {
        PublicRouteMatcher matcher = new PublicRouteMatcher(new SecurityRouteProperties());

        assertFalse(matcher.isPublic("/v1/auth/login"));
    }
}
