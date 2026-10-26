package com.snehal.neopay.common.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class FixedWindowRateLimiter implements RateLimiter {

    private final StringRedisTemplate redis;

    @Override
    public RateLimitResult check(String key, int maxRequestAllowed, long windowSeconds) {

        String redisKey = "ratelimit:fixed:"+key;

        try {
            Long count = redis.opsForValue().increment(redisKey);

            if (count == null) return RateLimitResult.allowed(maxRequestAllowed);

            Long ttl = redis.getExpire(redisKey, TimeUnit.SECONDS);
            if (count == 1 || (ttl != null && ttl == -1L)) {
                redis.expire(redisKey, Duration.ofSeconds(windowSeconds));
            }
            return evaluate(count, maxRequestAllowed, windowSeconds, redisKey, ttl);
        } catch (DataAccessException unavailable) {
            return RateLimitResult.allowed(maxRequestAllowed);
        }
    }

    private RateLimitResult evaluate(Long count, int maxRequestAllowed, long windowSeconds,
                                     String redisKey, Long ttl) {
        if (count > maxRequestAllowed) {
            Long currentTtl = ttl;
            if (currentTtl == null) {
                currentTtl = redis.getExpire(redisKey, TimeUnit.SECONDS);
            }
            int retryAfter = (currentTtl != null && currentTtl > 0)
                    ? currentTtl.intValue()
                    : (int) windowSeconds;
            return RateLimitResult.denied(retryAfter);
        }
        return RateLimitResult.allowed((int) (maxRequestAllowed - count));
    }
}
