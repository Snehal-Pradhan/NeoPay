package com.snehal.neopay.merchant.service.impl;

import com.snehal.neopay.common.cache.ApiKeyCache;
import com.snehal.neopay.common.cache.ApiKeyCacheEntry;
import com.snehal.neopay.common.exception.BusinessRuleViolationException;
import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.common.util.RandomizerUtil;
import com.snehal.neopay.merchant.dto.request.CreateApiKeyRequest;
import com.snehal.neopay.merchant.dto.response.ApiKeyCreateResponse;
import com.snehal.neopay.merchant.dto.response.ApiKeyResponse;
import com.snehal.neopay.merchant.entity.ApiKey;
import com.snehal.neopay.merchant.entity.Merchant;
import com.snehal.neopay.merchant.mapper.ApiKeyMapper;
import com.snehal.neopay.merchant.repository.ApiKeyRepository;
import com.snehal.neopay.merchant.repository.MerchantRepository;
import com.snehal.neopay.merchant.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ApiKeyServiceImpl implements ApiKeyService {

    private final MerchantRepository merchantRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyMapper apiKeyMapper;
    private BCryptPasswordEncoder BCRPYT = new BCryptPasswordEncoder();
    private final ApiKeyCache apiKeyCache;

    @Override
    @Transactional
    public ApiKeyCreateResponse create(UUID merchantId, CreateApiKeyRequest request) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("merchant", merchantId));

        String keyId = "np_"+request.environment().name().toLowerCase()+"_"+ RandomizerUtil.randomBase64(24);
        String rawSecret = RandomizerUtil.randomBase64(40);

        ApiKey apiKey = ApiKey.builder()
                .merchant(merchant)
                .keyId(keyId)
                .keySecretHash(BCRPYT.encode(rawSecret))
                .environment(request.environment())
                .build();

        apiKey = apiKeyRepository.save(apiKey);

        return new ApiKeyCreateResponse(apiKey.getId(), keyId, rawSecret, request.environment());
    }

    @Override
    public Page<ApiKeyResponse> listByMerchant(UUID merchantId, Pageable pageable) {
        return apiKeyRepository.findByMerchant_Id(merchantId, pageable)
                .map(apiKeyMapper::toResponse);
    }

    @Override
    @Transactional
    public void revoke(UUID merchantId, UUID keyId) {
        ApiKey key = apiKeyRepository.findById(keyId)
                .filter(k -> k.getMerchant().getId().equals(merchantId))
                .orElseThrow(() -> new ResourceNotFoundException("ApiKey", keyId));

        key.setEnabled(false);
        apiKeyCache.evict(key.getKeyId());
    }

    @Override
    @Transactional
    public ApiKeyCreateResponse rotate(UUID merchantId, UUID keyId) {
        ApiKey apiKey = apiKeyRepository.findById(keyId)
                .filter(k -> k.getMerchant().getId().equals(merchantId))
                .orElseThrow(() -> new ResourceNotFoundException("ApiKey", keyId));

        if (!apiKey.isEnabled()) {
            throw new BusinessRuleViolationException("API_KEY_DISABLED",
                    "Cannot rotate a disabled key");
        }

        String newRawSecret = RandomizerUtil.randomBase64(40);
        apiKey.setPreviousKeySecretHash(apiKey.getKeySecretHash());
        apiKey.setKeySecretHash(BCRPYT.encode(newRawSecret));
        apiKey.setRotatedAt(LocalDateTime.now());
        apiKey.setGracePeriodExpiresAt(LocalDateTime.now().plusHours(24));
        apiKey = apiKeyRepository.save(apiKey);

        apiKeyCache.evict(apiKey.getKeyId());

        return new ApiKeyCreateResponse(apiKey.getId(), apiKey.getKeyId(),
                newRawSecret, apiKey.getEnvironment());
    }

    @Override
    public ApiKeyCacheEntry findCacheEntry(String keyId) {
        ApiKey apiKey = apiKeyRepository.findByKeyId(keyId)
                .orElseThrow(() -> new ResourceNotFoundException("ApiKey", keyId));

        return new ApiKeyCacheEntry(
                apiKey.getKeyId(),
                apiKey.getKeySecretHash(),
                apiKey.getPreviousKeySecretHash(),
                apiKey.getGracePeriodExpiresAt(),
                apiKey.getMerchant().getId(),
                apiKey.getEnvironment(),
                apiKey.isEnabled());
    }

}



















