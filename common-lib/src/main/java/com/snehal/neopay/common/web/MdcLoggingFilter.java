package com.snehal.neopay.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds correlation fields to the logging MDC for the duration of a request.
 * Trace and span identifiers are added by Micrometer Tracing when tracing is
 * active; this filter only adds request and identity fields.
 */
public class MdcLoggingFilter extends OncePerRequestFilter {

    public static final String MERCHANT_ID = "merchantId";
    public static final String KEY_ID = "keyId";
    public static final String PATH = "path";
    public static final String METHOD = "method";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            MDC.put(PATH, request.getRequestURI());
            MDC.put(METHOD, request.getMethod());

            String merchantId = request.getHeader(MerchantContextFilter.MERCHANT_ID_HEADER);
            if (merchantId != null && !merchantId.isBlank()) {
                MDC.put(MERCHANT_ID, merchantId);
            }

            String keyId = request.getHeader(MerchantContextFilter.KEY_ID_HEADER);
            if (keyId != null && !keyId.isBlank()) {
                MDC.put(KEY_ID, keyId);
            }

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(PATH);
            MDC.remove(METHOD);
            MDC.remove(MERCHANT_ID);
            MDC.remove(KEY_ID);
        }
    }
}
