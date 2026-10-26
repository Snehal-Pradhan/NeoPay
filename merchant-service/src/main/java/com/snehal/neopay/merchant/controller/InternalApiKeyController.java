package com.snehal.neopay.merchant.controller;

import com.snehal.neopay.common.cache.ApiKeyCacheEntry;
import com.snehal.neopay.merchant.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/api-keys")
public class InternalApiKeyController {

    private final ApiKeyService apiKeyService;

    @GetMapping("/{keyId}")
    public ApiKeyCacheEntry findByKeyId(@PathVariable String keyId) {
        return apiKeyService.findCacheEntry(keyId);
    }
}
