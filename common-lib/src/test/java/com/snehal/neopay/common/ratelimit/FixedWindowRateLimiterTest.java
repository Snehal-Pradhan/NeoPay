package com.snehal.neopay.common.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FixedWindowRateLimiterTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private FixedWindowRateLimiter limiter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        limiter = new FixedWindowRateLimiter(redis);
    }

    @Test
    void firstRequestStartsWindowAndIsAllowed() {
        when(valueOps.increment("ratelimit:fixed:merchant")).thenReturn(1L);

        RateLimitResult result = limiter.check("merchant", 10, 60);

        assertTrue(result.isAllowed());
        assertEquals(9, result.remaining());
        verify(redis).expire("ratelimit:fixed:merchant", Duration.ofSeconds(60));
    }

    @Test
    void requestWithinLimitIsAllowed() {
        when(valueOps.increment("ratelimit:fixed:merchant")).thenReturn(4L);

        RateLimitResult result = limiter.check("merchant", 10, 60);

        assertTrue(result.isAllowed());
        assertEquals(6, result.remaining());
        verify(redis, never()).expire("ratelimit:fixed:merchant", Duration.ofSeconds(60));
    }

    @Test
    void requestOverLimitIsDeniedWithTtl() {
        when(valueOps.increment("ratelimit:fixed:merchant")).thenReturn(11L);
        when(redis.getExpire("ratelimit:fixed:merchant", TimeUnit.SECONDS)).thenReturn(7L);

        RateLimitResult result = limiter.check("merchant", 10, 60);

        assertFalse(result.isAllowed());
        assertEquals(7, result.retryAfterSeconds());
    }

    @Test
    void nullTtlFallsBackToWindowWithoutThrowing() {
        when(valueOps.increment("ratelimit:fixed:merchant")).thenReturn(11L);
        when(redis.getExpire("ratelimit:fixed:merchant", TimeUnit.SECONDS)).thenReturn(null);

        RateLimitResult result = limiter.check("merchant", 10, 60);

        assertFalse(result.isAllowed());
        assertEquals(60, result.retryAfterSeconds());
    }

    @Test
    void zeroTtlFallsBackToWindow() {
        when(valueOps.increment("ratelimit:fixed:merchant")).thenReturn(11L);
        when(redis.getExpire("ratelimit:fixed:merchant", TimeUnit.SECONDS)).thenReturn(0L);

        assertEquals(60, limiter.check("merchant", 10, 60).retryAfterSeconds());
    }

    @Test
    void nullIncrementFailsOpen() {
        when(valueOps.increment("ratelimit:fixed:merchant")).thenReturn(null);

        RateLimitResult result = limiter.check("merchant", 10, 60);

        assertTrue(result.isAllowed());
        assertEquals(10, result.remaining());
    }

    @Test
    void requestExactlyAtLimitIsAllowedWithNoRemaining() {
        when(valueOps.increment("ratelimit:fixed:merchant")).thenReturn(10L);

        RateLimitResult result = limiter.check("merchant", 10, 60);

        assertTrue(result.isAllowed());
        assertEquals(0, result.remaining());
    }

    @Test
    void missingTtlIsRepairedForAnExistingWindow() {
        when(valueOps.increment("ratelimit:fixed:merchant")).thenReturn(3L);
        when(redis.getExpire("ratelimit:fixed:merchant", TimeUnit.SECONDS)).thenReturn(-1L);

        RateLimitResult result = limiter.check("merchant", 10, 60);

        assertTrue(result.isAllowed());
        assertEquals(7, result.remaining());
        verify(redis).expire("ratelimit:fixed:merchant", Duration.ofSeconds(60));
    }
}
