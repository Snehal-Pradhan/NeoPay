package com.snehal.neopay.gateway.security;

import com.snehal.neopay.gateway.client.ApiKeyLookupClient;
import com.snehal.neopay.common.cache.ApiKeyCache;
import com.snehal.neopay.common.cache.ApiKeyCacheEntry;
import com.snehal.neopay.common.enums.Environment;
import com.snehal.neopay.common.exception.RateLimitException;
import com.snehal.neopay.common.ratelimit.RateLimitResult;
import com.snehal.neopay.common.ratelimit.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyAuthHandlerTest {

    private static final String SECRET = "super-secret";
    private static final String KEY_ID = "np_test_abc";

    private ApiKeyCache apiKeyCache;
    private ApiKeyLookupClient lookupClient;
    private RateLimiter rateLimiter;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private ApiKeyAuthHandler handler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        apiKeyCache = mock(ApiKeyCache.class);
        lookupClient = mock(ApiKeyLookupClient.class);
        rateLimiter = mock(RateLimiter.class);
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(rateLimiter.check(anyString(), anyInt(), anyLong()))
                .thenReturn(RateLimitResult.allowed(59));
        handler = new ApiKeyAuthHandler(apiKeyCache, lookupClient, rateLimiter, redis);
    }

    @Test
    void validCachedKeyReturnsIdentityHeaders() {
        ApiKeyCacheEntry cached = entry(hash(SECRET), null);
        when(apiKeyCache.get(KEY_ID)).thenReturn(Optional.of(cached));
        when(redis.hasKey(anyString())).thenReturn(false);

        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, String> headers = handler.authenticate(basic(KEY_ID, SECRET), response);

        assertEquals(cached.merchantId().toString(), headers.get("X-Merchant-Id"));
        assertEquals("TEST", headers.get("X-Environment"));
        assertEquals(KEY_ID, headers.get("X-Key-Id"));
        assertEquals("59", response.getHeader("X-RateLimit-Remaining"));
    }

    @Test
    void cacheMissLoadsAndCachesFromMerchantService() {
        when(apiKeyCache.get(KEY_ID)).thenReturn(Optional.empty());
        when(lookupClient.findByKeyId(KEY_ID)).thenReturn(entry(hash(SECRET), null));
        when(redis.hasKey(anyString())).thenReturn(false);

        handler.authenticate(basic(KEY_ID, SECRET), new MockHttpServletResponse());

        verify(apiKeyCache).put(eq(KEY_ID), any(ApiKeyCacheEntry.class));
    }

    @Test
    void lookupFailureRejects() {
        when(apiKeyCache.get(KEY_ID)).thenReturn(Optional.empty());
        when(lookupClient.findByKeyId(KEY_ID)).thenThrow(new IllegalStateException("down"));

        assertThrows(GatewayAuthenticationException.class,
                () -> handler.authenticate(basic(KEY_ID, SECRET), new MockHttpServletResponse()));
    }

    @Test
    void malformedHeaderRejects() {
        assertThrows(GatewayAuthenticationException.class,
                () -> handler.authenticate("Basic !!!", new MockHttpServletResponse()));
        assertThrows(GatewayAuthenticationException.class,
                () -> handler.authenticate("Bearer token", new MockHttpServletResponse()));
    }

    @Test
    void disabledKeyRejects() {
        ApiKeyCacheEntry disabled = new ApiKeyCacheEntry(KEY_ID, hash(SECRET), null, null,
                UUID.randomUUID(), Environment.TEST, false);
        when(apiKeyCache.get(KEY_ID)).thenReturn(Optional.of(disabled));

        assertThrows(GatewayAuthenticationException.class,
                () -> handler.authenticate(basic(KEY_ID, SECRET), new MockHttpServletResponse()));
    }

    @Test
    void wrongSecretRejects() {
        when(apiKeyCache.get(KEY_ID)).thenReturn(Optional.of(entry(hash(SECRET), null)));
        when(redis.hasKey(anyString())).thenReturn(false);

        assertThrows(GatewayAuthenticationException.class,
                () -> handler.authenticate(basic(KEY_ID, "wrong"), new MockHttpServletResponse()));
    }

    @Test
    void previousSecretWorksDuringGracePeriod() {
        ApiKeyCacheEntry entry = new ApiKeyCacheEntry(KEY_ID, hash("new"), hash("old"),
                java.time.LocalDateTime.now().plusHours(1), UUID.randomUUID(), Environment.TEST, true);
        when(apiKeyCache.get(KEY_ID)).thenReturn(Optional.of(entry));
        when(redis.hasKey(anyString())).thenReturn(false);

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> handler.authenticate(basic(KEY_ID, "old"), new MockHttpServletResponse()));
    }

    @Test
    void verifiedSecretIsServedFromCache() {
        ApiKeyCacheEntry entry = entry(hash(SECRET), null);
        when(apiKeyCache.get(KEY_ID)).thenReturn(Optional.of(entry));
        when(redis.hasKey(anyString())).thenReturn(true);

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> handler.authenticate(basic(KEY_ID, "any"), new MockHttpServletResponse()));
    }

    @Test
    void rateLimitedRequestRejects() {
        when(apiKeyCache.get(KEY_ID)).thenReturn(Optional.of(entry(hash(SECRET), null)));
        when(redis.hasKey(anyString())).thenReturn(false);
        when(rateLimiter.check(anyString(), anyInt(), anyLong()))
                .thenReturn(RateLimitResult.denied(12));

        assertThrows(RateLimitException.class,
                () -> handler.authenticate(basic(KEY_ID, SECRET), new MockHttpServletResponse()));
    }

    private static ApiKeyCacheEntry entry(String secretHash, String previousHash) {
        return new ApiKeyCacheEntry(KEY_ID, secretHash, previousHash, null,
                UUID.randomUUID(), Environment.TEST, true);
    }

    private static String hash(String raw) {
        return new BCryptPasswordEncoder().encode(raw);
    }

    private static String basic(String keyId, String secret) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((keyId + ":" + secret).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rateLimitIsAppliedBeforePasswordVerification() {
        when(apiKeyCache.get(KEY_ID)).thenReturn(Optional.of(entry(hash(SECRET), null)));
        when(rateLimiter.check(anyString(), anyInt(), anyLong()))
                .thenReturn(RateLimitResult.denied(9));

        assertThrows(RateLimitException.class,
                () -> handler.authenticate(basic(KEY_ID, SECRET), new MockHttpServletResponse()));
        org.mockito.Mockito.verify(redis, org.mockito.Mockito.never()).hasKey(anyString());
    }
}
