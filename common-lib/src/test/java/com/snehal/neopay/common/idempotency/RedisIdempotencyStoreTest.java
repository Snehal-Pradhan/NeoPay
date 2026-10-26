package com.snehal.neopay.common.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisIdempotencyStoreTest {

    private static final Duration TTL = Duration.ofHours(24);
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private RedisIdempotencyStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        store = new RedisIdempotencyStore(redis);
    }

    @Test
    void firstClaimSucceeds() {
        when(valueOps.setIfAbsent("idempotency:k1", IdempotencyStore.IN_PROGRESS, TTL)).thenReturn(true);

        assertTrue(store.setIfAbsent("k1", TTL));
    }

    @Test
    void secondClaimFails() {
        when(valueOps.setIfAbsent("idempotency:k1", IdempotencyStore.IN_PROGRESS, TTL)).thenReturn(false);

        assertFalse(store.setIfAbsent("k1", TTL));
    }

    @Test
    void claimFailsOpenWhenRedisIsUnavailable() {
        when(valueOps.setIfAbsent("idempotency:k1", IdempotencyStore.IN_PROGRESS, TTL))
                .thenThrow(new DataAccessResourceFailureException("down"));

        assertTrue(store.setIfAbsent("k1", TTL));
    }

    @Test
    void storeUsesPrefixedKey() {
        store.store("k1", "payload", TTL);

        verify(valueOps).set("idempotency:k1", "payload", TTL);
    }

    @Test
    void storeSwallowsRedisFailure() {
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("down"))
                .when(valueOps).set("idempotency:k1", "payload", TTL);

        assertDoesNotThrow(() -> store.store("k1", "payload", TTL));
    }

    @Test
    void getReturnsStoredValue() {
        when(valueOps.get("idempotency:k1")).thenReturn("payload");

        assertEquals(Optional.of("payload"), store.get("k1"));
    }

    @Test
    void getReturnsEmptyOnMiss() {
        when(valueOps.get("idempotency:k1")).thenReturn(null);

        assertEquals(Optional.empty(), store.get("k1"));
    }

    @Test
    void getFailsOpenOnRedisFailure() {
        when(valueOps.get("idempotency:k1"))
                .thenThrow(new DataAccessResourceFailureException("down"));

        assertEquals(Optional.empty(), store.get("k1"));
    }

    @Test
    void deleteUsesPrefixedKey() {
        store.delete("k1");

        verify(redis).delete("idempotency:k1");
    }
}
