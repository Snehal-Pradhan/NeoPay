package com.snehal.neopay.common.cache;

import com.snehal.neopay.common.enums.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisApiKeyCacheTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private ObjectMapper objectMapper;
    private RedisApiKeyCache cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        objectMapper = mock(ObjectMapper.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        cache = new RedisApiKeyCache(redis, objectMapper);
    }

    @Test
    void getReturnsCachedEntry() throws Exception {
        ApiKeyCacheEntry entry = entry();
        when(valueOps.get("apikey:np_test_1")).thenReturn("{json}");
        when(objectMapper.readValue("{json}", ApiKeyCacheEntry.class)).thenReturn(entry);

        assertEquals(Optional.of(entry), cache.get("np_test_1"));
    }

    @Test
    void getReturnsEmptyOnMiss() {
        when(valueOps.get("apikey:missing")).thenReturn(null);

        assertEquals(Optional.empty(), cache.get("missing"));
    }

    @Test
    void getFailsOpenOnDeserializationError() throws Exception {
        when(valueOps.get("apikey:bad")).thenReturn("{json}");
        when(objectMapper.readValue(anyString(), eq(ApiKeyCacheEntry.class)))
                .thenThrow(new RuntimeException("bad json"));

        assertEquals(Optional.empty(), cache.get("bad"));
    }

    @Test
    void putSerializesWithTtl() throws Exception {
        ApiKeyCacheEntry entry = entry();
        when(objectMapper.writeValueAsString(entry)).thenReturn("{json}");

        cache.put("np_test_1", entry);

        verify(valueOps).set("apikey:np_test_1", "{json}", Duration.ofMinutes(5));
    }

    @Test
    void putSwallowsSerializationFailure() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(() -> cache.put("np_test_1", entry()));
    }

    @Test
    void evictDeletesPrefixedKey() {
        cache.evict("np_test_1");

        verify(redis).delete("apikey:np_test_1");
    }

    private static ApiKeyCacheEntry entry() {
        return new ApiKeyCacheEntry("np_test_1", "hash", null, null,
                UUID.randomUUID(), Environment.TEST, true);
    }
}
