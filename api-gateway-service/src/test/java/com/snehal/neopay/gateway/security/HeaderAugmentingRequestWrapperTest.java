package com.snehal.neopay.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class HeaderAugmentingRequestWrapperTest {

    private static MockHttpServletRequest clientRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payments");
        request.addHeader("X-Merchant-Id", "client-chosen-merchant");
        request.addHeader("X-Key-Id", "client-chosen-key");
        request.addHeader("X-Environment", "LIVE");
        request.addHeader("X-Request-Id", "req-1");
        return request;
    }

    @Test
    void clientSuppliedIdentityHeadersAreHidden() {
        HeaderAugmentingRequestWrapper wrapper = new HeaderAugmentingRequestWrapper(clientRequest());

        assertNull(wrapper.getHeader("X-Merchant-Id"));
        assertNull(wrapper.getHeader("x-merchant-id"));
        assertNull(wrapper.getHeader("X-Key-Id"));
        assertNull(wrapper.getHeader("X-Environment"));
        assertFalse(wrapper.getHeaders("X-Merchant-Id").hasMoreElements());
        assertFalse(Collections.list(wrapper.getHeaderNames()).contains("X-Merchant-Id"));
    }

    @Test
    void ordinaryHeadersPassThroughUnchanged() {
        HeaderAugmentingRequestWrapper wrapper = new HeaderAugmentingRequestWrapper(clientRequest());

        assertEquals("req-1", wrapper.getHeader("X-Request-Id"));
    }

    @Test
    void gatewayInjectedIdentityWinsOverClientValue() {
        HeaderAugmentingRequestWrapper wrapper = new HeaderAugmentingRequestWrapper(clientRequest());
        wrapper.putHeader("X-Merchant-Id", "trusted-merchant");

        assertEquals("trusted-merchant", wrapper.getHeader("X-Merchant-Id"));
        assertEquals("trusted-merchant", wrapper.getHeader("x-merchant-id"));
        assertEquals(1, Collections.list(wrapper.getHeaders("X-Merchant-Id")).size());
    }

    @Test
    void injectedHeadersAppearInTheNameEnumeration() {
        HeaderAugmentingRequestWrapper wrapper = new HeaderAugmentingRequestWrapper(clientRequest());
        wrapper.putHeader("X-Key-Id", "np_test_1");

        assertEquals(1, Collections.list(wrapper.getHeaderNames()).stream()
                .filter(name -> name.toLowerCase().equals("x-key-id"))
                .count());
    }
}
