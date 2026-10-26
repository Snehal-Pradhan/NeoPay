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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenBucketRateLimiterTest {

    private StringRedisTemplate redis;
    private TokenBucketRateLimiter limiter;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        limiter = new TokenBucketRateLimiter(redis);
    }

    @Test
    @SuppressWarnings("unchecked")
    void allowedResponseMapsRemaining() {
        when(redis.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 7L, 0L));

        RateLimitResult result = limiter.check("merchant", 10, 60);

        assertTrue(result.isAllowed());
        assertEquals(7, result.remaining());
    }

    @Test
    @SuppressWarnings("unchecked")
    void deniedResponseMapsRetryAfter() {
        when(redis.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(List.of(0L, 0L, 12L));

        RateLimitResult result = limiter.check("merchant", 10, 60);

        assertFalse(result.isAllowed());
        assertEquals(12, result.retryAfterSeconds());
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisFailureFailsOpen() {
        when(redis.execute(any(), anyList(), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("down"));

        RateLimitResult result = limiter.check("merchant", 10, 60);

        assertTrue(result.isAllowed());
        assertEquals(10, result.remaining());
    }
}
