package com.snehal.neopay.gateway.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HeaderAugmentingRequestWrapper extends HttpServletRequestWrapper {

    /** Headers that a client must never be able to supply; only the gateway may set them. */
    private static final Set<String> PROTECTED_HEADERS = Set.of(
            "x-merchant-id", "x-key-id", "x-environment", "x-user-role");

    private final Map<String, String> extraHeaders = new LinkedHashMap<>();

    public HeaderAugmentingRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public String getAuthType() {
        return super.getAuthType();
    }

    public void putHeader(String name, String value) {
        extraHeaders.put(name.toLowerCase(Locale.ROOT), value);
    }

    @Override
    public String getHeader(String name) {
        String key = name == null ? null : name.toLowerCase(Locale.ROOT);
        String value = key == null ? null : extraHeaders.get(key);
        if (value != null) {
            return value;
        }
        if (key != null && PROTECTED_HEADERS.contains(key)) {
            return null;
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String key = name == null ? null : name.toLowerCase(Locale.ROOT);
        String value = key == null ? null : extraHeaders.get(key);
        if (value != null) {
            return Collections.enumeration(Collections.singletonList(value));
        }
        if (key != null && PROTECTED_HEADERS.contains(key)) {
            return Collections.emptyEnumeration();
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = new LinkedHashSet<>(extraHeaders.keySet());
        Collections.list(super.getHeaderNames()).forEach(name -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!PROTECTED_HEADERS.contains(lower)) {
                names.add(name);
            }
        });
        return Collections.enumeration(names);
    }
}
