package com.snehal.neopay.operations.webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookRetryQueueTest {

    private StringRedisTemplate redis;
    private ZSetOperations<String, String> zset;
    private WebhookRetryQueue queue;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        zset = mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zset);
        queue = new WebhookRetryQueue(redis);
    }

    @Test
    void enqueueAddsToSortedSet() {
        UUID id = UUID.randomUUID();

        queue.enqueue(id, LocalDateTime.now().plusMinutes(5));

        verify(zset).add(any(), eq(id.toString()), anyDouble());
    }

    @Test
    void pollDueReturnsEmptyWhenNothingIsDue() {
        when(zset.rangeByScoreWithScores(any(), anyDouble(), anyDouble(), anyLong(), anyLong()))
                .thenReturn(Set.of());

        assertTrue(queue.pollDue(10).isEmpty());
    }

    @Test
    void pollDueReturnsIdsAndRemovesThem() {
        UUID id = UUID.randomUUID();
        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);
        when(tuple.getValue()).thenReturn(id.toString());
        when(zset.rangeByScoreWithScores(any(), anyDouble(), anyDouble(), anyLong(), anyLong()))
                .thenReturn(Set.of(tuple));

        Set<UUID> due = queue.pollDue(10);

        assertEquals(Set.of(id), due);
        verify(zset).remove(any(), eq(id.toString()));
    }
}
