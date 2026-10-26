package com.snehal.neopay.common.idempotency;

import com.snehal.neopay.common.context.MerchantContext;
import com.snehal.neopay.common.exception.IdempotencyConflictException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyFilterTest {

    private IdempotencyStore store;
    private HandlerExceptionResolver resolver;
    private IdempotencyFilter filter;
    private MerchantContext context;

    @BeforeEach
    void setUp() {
        store = mock(IdempotencyStore.class);
        resolver = mock(HandlerExceptionResolver.class);
        context = new MerchantContext();
        filter = new IdempotencyFilter(context, store, resolver);
    }

    @Test
    void nonGuardedMethodIsPassedThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(store, never()).setIfAbsent(any(), any());
    }

    @Test
    void missingKeyIsPassedThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/payments");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(store, never()).setIfAbsent(any(), any());
    }

    @Test
    void successfulResponseIsStored() throws Exception {
        UUID merchantId = UUID.randomUUID();
        context.setMerchantId(merchantId);
        MockHttpServletRequest request = postWithKey("key-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            jakarta.servlet.http.HttpServletResponse http = (jakarta.servlet.http.HttpServletResponse) res;
            http.setStatus(201);
            http.getWriter().write("{\"id\":\"p1\"}");
        };

        when(store.setIfAbsent(eq(merchantId + ":key-1"), any(Duration.class))).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertEquals(201, response.getStatus());
        verify(store).store(eq(merchantId + ":key-1"), eq("201|{\"id\":\"p1\"}"), any(Duration.class));
    }

    @Test
    void errorResponseClearsPlaceholder() throws Exception {
        MockHttpServletRequest request = postWithKey("key-2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(400);

        when(store.setIfAbsent(eq("key-2"), any(Duration.class))).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(store).delete("key-2");
    }

    @Test
    void completedResponseIsReplayed() throws Exception {
        MockHttpServletRequest request = postWithKey("key-3");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(store.setIfAbsent(eq("key-3"), any(Duration.class))).thenReturn(false);
        when(store.get("key-3")).thenReturn(Optional.of("200|{\"id\":\"p1\"}"));

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertEquals("{\"id\":\"p1\"}", response.getContentAsString());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void inProgressReturnsConflict() throws Exception {
        MockHttpServletRequest request = postWithKey("key-4");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(store.setIfAbsent(eq("key-4"), any(Duration.class))).thenReturn(false);
        when(store.get("key-4")).thenReturn(Optional.of(IdempotencyStore.IN_PROGRESS));

        filter.doFilter(request, response, chain);

        verify(resolver).resolveException(eq(request), eq(response), isNull(),
                any(IdempotencyConflictException.class));
        verify(chain, never()).doFilter(any(), any());
    }

    private static MockHttpServletRequest postWithKey(String key) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/payments");
        request.addHeader("X-Idempotency-Key", key);
        return request;
    }
}
