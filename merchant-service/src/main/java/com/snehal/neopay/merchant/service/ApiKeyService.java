package com.snehal.neopay.merchant.service;

import com.snehal.neopay.common.cache.ApiKeyCacheEntry;
import com.snehal.neopay.merchant.dto.request.CreateApiKeyRequest;
import com.snehal.neopay.merchant.dto.response.ApiKeyCreateResponse;
import com.snehal.neopay.merchant.dto.response.ApiKeyResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ApiKeyService {

    ApiKeyCreateResponse create(UUID merchantId, CreateApiKeyRequest request);

    Page<ApiKeyResponse> listByMerchant(UUID merchantId, Pageable pageable);

    void revoke(UUID merchantId, UUID keyId);

    @Nullable ApiKeyCreateResponse rotate(UUID merchantId, UUID keyId);

    ApiKeyCacheEntry findCacheEntry(String keyId);
}
