package com.snehal.neopay.common.web;

import com.snehal.neopay.common.context.MerchantContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebFiltersTest {

    @Test
    void merchantContextFilterReadsIdentityHeaders() throws Exception {
        MerchantContext context = new MerchantContext();
        UUID merchantId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(MerchantContextFilter.MERCHANT_ID_HEADER, merchantId.toString());
        request.addHeader(MerchantContextFilter.KEY_ID_HEADER, "np_test_key");

        new MerchantContextFilter(context).doFilter(request,
                new MockHttpServletResponse(), (req, res) -> { });

        assertEquals(merchantId, context.getMerchantId());
        assertEquals("np_test_key", context.getKeyId());
    }

    @Test
    void merchantContextFilterIgnoresMissingHeaders() throws Exception {
        MerchantContext context = new MerchantContext();

        new MerchantContextFilter(context).doFilter(new MockHttpServletRequest(),
                new MockHttpServletResponse(), (req, res) -> { });

        assertNull(context.getMerchantId());
        assertNull(context.getKeyId());
    }

    @Test
    void mdcFilterSetsAndClearsFields() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/payments");
        request.addHeader(MerchantContextFilter.MERCHANT_ID_HEADER, "merchant-1");
        request.addHeader(MerchantContextFilter.KEY_ID_HEADER, "key-1");

        AtomicReference<String> observedMerchant = new AtomicReference<>();
        AtomicReference<String> observedPath = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            observedMerchant.set(MDC.get(MdcLoggingFilter.MERCHANT_ID));
            observedPath.set(MDC.get(MdcLoggingFilter.PATH));
        };

        new MdcLoggingFilter().doFilter(request, new MockHttpServletResponse(), chain);

        assertEquals("merchant-1", observedMerchant.get());
        assertEquals("/v1/payments", observedPath.get());
        assertNull(MDC.get(MdcLoggingFilter.MERCHANT_ID));
        assertNull(MDC.get(MdcLoggingFilter.PATH));
    }

    @Test
    void malformedMerchantIdIsRejectedWith400AndStopsChain() throws Exception {
        MerchantContext context = new MerchantContext();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/payments");
        request.addHeader(MerchantContextFilter.MERCHANT_ID_HEADER, "not-a-uuid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        new MerchantContextFilter(context).doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertEquals(400, response.getStatus());
        assertFalse(chainCalled.get());
        assertNull(context.getMerchantId());
    }

    @Test
    void blankMerchantIdIsIgnoredAndChainContinues() throws Exception {
        MerchantContext context = new MerchantContext();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(MerchantContextFilter.MERCHANT_ID_HEADER, "   ");
        request.addHeader(MerchantContextFilter.KEY_ID_HEADER, "  ");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        new MerchantContextFilter(context).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> chainCalled.set(true));

        assertTrue(chainCalled.get());
        assertNull(context.getMerchantId());
        assertNull(context.getKeyId());
    }

    @Test
    void merchantIdIsTrimmedBeforeParsing() throws Exception {
        MerchantContext context = new MerchantContext();
        UUID merchantId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(MerchantContextFilter.MERCHANT_ID_HEADER, "  " + merchantId + "  ");

        new MerchantContextFilter(context).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> { });

        assertEquals(merchantId, context.getMerchantId());
    }

    @Test
    void mdcFilterPublishesKeyIdAndClearsItAfterFailure() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/orders");
        request.addHeader(MerchantContextFilter.KEY_ID_HEADER, "key-9");
        AtomicReference<String> observedKey = new AtomicReference<>();

        FilterChain failingChain = (req, res) -> {
            observedKey.set(MDC.get(MdcLoggingFilter.KEY_ID));
            throw new IllegalStateException("downstream failure");
        };

        assertThrows(IllegalStateException.class, () -> new MdcLoggingFilter()
                .doFilter(request, new MockHttpServletResponse(), failingChain));

        assertEquals("key-9", observedKey.get());
        assertNull(MDC.get(MdcLoggingFilter.KEY_ID));
        assertNull(MDC.get(MdcLoggingFilter.PATH));
        assertNull(MDC.get(MdcLoggingFilter.METHOD));
    }
}
