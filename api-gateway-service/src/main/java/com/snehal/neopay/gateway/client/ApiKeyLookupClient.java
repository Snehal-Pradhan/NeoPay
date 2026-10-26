package com.snehal.neopay.gateway.client;

import com.snehal.neopay.common.cache.ApiKeyCacheEntry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "merchant-service", path = "/internal/api-keys", url = "${MERCHANT_SERVICE_URI:}")
public interface ApiKeyLookupClient {

    @GetMapping("/{keyId}")
    ApiKeyCacheEntry findByKeyId(@PathVariable String keyId);
}
