package com.snehal.neopay.common.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SlidingWindowLuaLimiterTest {

    private StringRedisTemplate redis;
    private SlidingWindowLuaLimiter limiter;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        limiter = new SlidingWindowLuaLimiter(redis);
    }

    @Test
    @SuppressWarnings("unchecked")
    void allowedResultMapsRemaining() {
        when(redis.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 4L, 0L));

        RateLimitResult result = limiter.check("merchant", 10, 60);

        assertTrue(result.isAllowed());
        assertEquals(4, result.remaining());
    }

    @Test
    @SuppressWarnings("unchecked")
    void deniedWithOldestScoreComputesRetry() {
        long oldest = System.currentTimeMillis() - 10_000;
        when(redis.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(List.of(0L, 0L, oldest));

        RateLimitResult result = limiter.check("merchant", 10, 60);

        assertFalse(result.isAllowed());
        assertTrue(result.retryAfterSeconds() >= 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deniedWithZeroScoreFallsBackToWindow() {
        when(redis.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(List.of(0L, 0L, 0L));

        assertEquals(60, limiter.check("merchant", 10, 60).retryAfterSeconds());
    }

    @Test
    @SuppressWarnings("unchecked")
    void nullResultFailsOpen() {
        when(redis.execute(any(), anyList(), any(Object[].class))).thenReturn(null);

        RateLimitResult result = limiter.check("merchant", 10, 60);

        assertTrue(result.isAllowed());
        assertEquals(10, result.remaining());
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisFailureFailsOpen() {
        when(redis.execute(any(), anyList(), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("down"));

        assertTrue(limiter.check("merchant", 10, 60).isAllowed());
    }
}
