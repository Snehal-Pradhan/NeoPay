package com.snehal.neopay.gateway.security;

import com.snehal.neopay.common.exception.RateLimitException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayAuthFilterTest {

    private JwtAuthHandler jwtAuthHandler;
    private ApiKeyAuthHandler apiKeyAuthHandler;
    private PublicRouteMatcher publicRouteMatcher;
    private GatewayAuthFilter filter;

    @BeforeEach
    void setUp() {
        jwtAuthHandler = mock(JwtAuthHandler.class);
        apiKeyAuthHandler = mock(ApiKeyAuthHandler.class);
        publicRouteMatcher = mock(PublicRouteMatcher.class);
        filter = new GatewayAuthFilter(jwtAuthHandler, apiKeyAuthHandler, publicRouteMatcher,
                mock(ObjectMapper.class));
    }

    @Test
    void publicRoutePassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(publicRouteMatcher.isPublic("/v1/auth/login")).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void missingAuthorizationIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payments");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("application/problem+json", response.getContentType());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void basicAuthInjectsIdentityHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payments");
        request.addHeader("Authorization", "Basic abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(apiKeyAuthHandler.authenticate(anyString(), any()))
                .thenReturn(Map.of("X-Merchant-Id", "m-1"));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(HeaderAugmentingRequestWrapper.class), any());
        assertEquals(200, response.getStatus());
    }

    @Test
    void bearerAuthUsesJwtHandler() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/merchants/api-keys");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwtAuthHandler.authenticate("token")).thenReturn(Map.of("X-User-Role", "OWNER"));

        filter.doFilter(request, response, chain);

        verify(jwtAuthHandler).authenticate("token");
        verify(chain).doFilter(any(HeaderAugmentingRequestWrapper.class), any());
    }

    @Test
    void rateLimitMapsToTooManyRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payments");
        request.addHeader("Authorization", "Basic abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(apiKeyAuthHandler.authenticate(anyString(), any()))
                .thenThrow(new RateLimitException("rate limited", 30));

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertEquals("30", response.getHeader("Retry-After"));
    }

    @Test
    void authenticationFailureMapsToUnauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payments");
        request.addHeader("Authorization", "Basic abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(apiKeyAuthHandler.authenticate(anyString(), any()))
                .thenThrow(new GatewayAuthenticationException("bad key"));

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
    }

    @Test
    void unexpectedFailureMapsToUnauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payments");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwtAuthHandler.authenticate(anyString()))
                .thenThrow(new IllegalStateException("boom"));

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
    }

    @Test
    void downstreamFailureIsNotConvertedIntoUnauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payments/1");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            throw new IllegalStateException("downstream unavailable");
        };
        when(jwtAuthHandler.authenticate("token")).thenReturn(Map.of("X-Merchant-Id", "m-1"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> filter.doFilter(request, response, chain));
        assertEquals(200, response.getStatus());
    }

    @Test
    void clientSuppliedIdentityHeadersNeverReachDownstream() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/payments");
        request.addHeader("Authorization", "Basic abc");
        request.addHeader("X-Merchant-Id", "attacker-chosen");
        request.addHeader("X-Key-Id", "attacker-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        java.util.concurrent.atomic.AtomicReference<String> merchantSeen = new java.util.concurrent.atomic.AtomicReference<>("not-called");
        java.util.concurrent.atomic.AtomicReference<String> keySeen = new java.util.concurrent.atomic.AtomicReference<>("not-called");
        FilterChain chain = (req, res) -> {
            jakarta.servlet.http.HttpServletRequest http = (jakarta.servlet.http.HttpServletRequest) req;
            merchantSeen.set(http.getHeader("X-Merchant-Id"));
            keySeen.set(http.getHeader("X-Key-Id"));
        };
        when(apiKeyAuthHandler.authenticate(anyString(), any())).thenReturn(Map.of());

        filter.doFilter(request, response, chain);

        org.junit.jupiter.api.Assertions.assertNull(merchantSeen.get());
        org.junit.jupiter.api.Assertions.assertNull(keySeen.get());
    }
}
