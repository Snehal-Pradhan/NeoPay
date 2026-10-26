package com.snehal.neopay.merchant.service.impl;

import com.snehal.neopay.common.cache.ApiKeyCache;
import com.snehal.neopay.common.cache.ApiKeyCacheEntry;
import com.snehal.neopay.common.enums.Environment;
import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.merchant.dto.request.CreateApiKeyRequest;
import com.snehal.neopay.merchant.dto.response.ApiKeyCreateResponse;
import com.snehal.neopay.merchant.dto.response.ApiKeyResponse;
import com.snehal.neopay.merchant.entity.ApiKey;
import com.snehal.neopay.merchant.entity.Merchant;
import com.snehal.neopay.merchant.mapper.ApiKeyMapper;
import com.snehal.neopay.merchant.repository.ApiKeyRepository;
import com.snehal.neopay.merchant.repository.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyServiceImplTest {

    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private MerchantRepository merchantRepository;
    private ApiKeyRepository apiKeyRepository;
    private ApiKeyCache apiKeyCache;
    private ApiKeyMapper apiKeyMapper;
    private ApiKeyServiceImpl service;

    @BeforeEach
    void setUp() {
        merchantRepository = mock(MerchantRepository.class);
        apiKeyRepository = mock(ApiKeyRepository.class);
        apiKeyCache = mock(ApiKeyCache.class);
        apiKeyMapper = mock(ApiKeyMapper.class);
        service = new ApiKeyServiceImpl(merchantRepository, apiKeyRepository,
                apiKeyMapper, apiKeyCache);
    }

    @Test
    void createReturnsRawSecretOnceAndStoresOnlyItsHash() {
        Merchant merchant = mock(Merchant.class);
        when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(i -> i.getArgument(0));

        ApiKeyCreateResponse response = service.create(MERCHANT_ID,
                new CreateApiKeyRequest(Environment.TEST));

        assertNotNull(response.keySecret());
        assertTrue(response.keyId().startsWith("np_test_"));

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(captor.capture());
        ApiKey stored = captor.getValue();

        assertNotEquals(response.keySecret(), stored.getKeySecretHash());
        assertTrue(encoder.matches(response.keySecret(), stored.getKeySecretHash()));
        assertTrue(stored.isEnabled());
    }

    @Test
    void createRejectsUnknownMerchant() {
        when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.create(MERCHANT_ID, new CreateApiKeyRequest(Environment.TEST)));
    }

    @Test
    void revokeDisablesKeyAndEvictsCache() {
        Merchant merchant = mock(Merchant.class);
        when(merchant.getId()).thenReturn(MERCHANT_ID);
        ApiKey key = ApiKey.builder()
                .id(UUID.randomUUID())
                .merchant(merchant)
                .keyId("np_test_abc")
                .enabled(true)
                .build();
        when(apiKeyRepository.findById(key.getId())).thenReturn(Optional.of(key));

        service.revoke(MERCHANT_ID, key.getId());

        assertFalse(key.isEnabled());
        verify(apiKeyCache).evict("np_test_abc");
    }

    @Test
    void rotateKeepsPreviousHashAndSetsGracePeriod() {
        String oldRaw = "old-secret";
        Merchant merchant = mock(Merchant.class);
        when(merchant.getId()).thenReturn(MERCHANT_ID);
        ApiKey key = ApiKey.builder()
                .id(UUID.randomUUID())
                .merchant(merchant)
                .keyId("np_test_rotate")
                .keySecretHash(encoder.encode(oldRaw))
                .environment(Environment.TEST)
                .enabled(true)
                .build();
        when(apiKeyRepository.findById(key.getId())).thenReturn(Optional.of(key));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(i -> i.getArgument(0));

        ApiKeyCreateResponse response = service.rotate(MERCHANT_ID, key.getId());

        assertTrue(encoder.matches(oldRaw, key.getPreviousKeySecretHash()));
        assertTrue(encoder.matches(response.keySecret(), key.getKeySecretHash()));
        assertNotNull(key.getRotatedAt());
        assertNotNull(key.getGracePeriodExpiresAt());

        LocalDateTime expectedEarliest = LocalDateTime.now().plusHours(23);
        LocalDateTime expectedLatest = LocalDateTime.now().plusHours(25);
        assertTrue(key.getGracePeriodExpiresAt().isAfter(expectedEarliest));
        assertTrue(key.getGracePeriodExpiresAt().isBefore(expectedLatest));
        verify(apiKeyCache).evict("np_test_rotate");
    }

    @Test
    void rotateRejectsDisabledKey() {
        Merchant merchant = mock(Merchant.class);
        when(merchant.getId()).thenReturn(MERCHANT_ID);
        ApiKey key = ApiKey.builder()
                .id(UUID.randomUUID())
                .merchant(merchant)
                .keyId("np_test_disabled")
                .enabled(false)
                .build();
        when(apiKeyRepository.findById(key.getId())).thenReturn(Optional.of(key));

        com.snehal.neopay.common.exception.BusinessRuleViolationException error =
                assertThrows(com.snehal.neopay.common.exception.BusinessRuleViolationException.class,
                        () -> service.rotate(MERCHANT_ID, key.getId()));
        assertEquals("API_KEY_DISABLED", error.getErrorCode());
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void findCacheEntryMapsKeyState() {
        Merchant merchant = mock(Merchant.class);
        when(merchant.getId()).thenReturn(MERCHANT_ID);

        ApiKey key = ApiKey.builder()
                .merchant(merchant)
                .keyId("np_test_cache")
                .keySecretHash("hash")
                .previousKeySecretHash("prev")
                .environment(Environment.LIVE)
                .enabled(true)
                .build();
        when(apiKeyRepository.findByKeyId("np_test_cache")).thenReturn(Optional.of(key));

        ApiKeyCacheEntry entry = service.findCacheEntry("np_test_cache");

        assertEquals("np_test_cache", entry.keyId());
        assertEquals("hash", entry.keySecretHash());
        assertEquals(MERCHANT_ID, entry.merchantId());
        assertEquals(Environment.LIVE, entry.environment());
        assertTrue(entry.enabled());
    }

    @Test
    void findCacheEntryRejectsUnknownKey() {
        when(apiKeyRepository.findByKeyId("np_missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findCacheEntry("np_missing"));
    }

    @Test
    void listByMerchantReturnsPageMetadata() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("createdAt").descending());
        ApiKey key = ApiKey.builder().keyId("np_test_page").build();
        ApiKeyResponse response = new ApiKeyResponse(UUID.randomUUID(), "np_test_page",
                Environment.TEST, true, null, LocalDateTime.now());

        when(apiKeyRepository.findByMerchant_Id(MERCHANT_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(key), pageable, 1));
        when(apiKeyMapper.toResponse(key)).thenReturn(response);

        Page<ApiKeyResponse> page = service.listByMerchant(MERCHANT_ID, pageable);

        assertEquals(1, page.getTotalElements());
        assertEquals(1, page.getTotalPages());
        assertEquals(response, page.getContent().getFirst());
    }

    @Test
    void revokeRejectsUnknownKey() {
        UUID keyId = UUID.randomUUID();
        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.revoke(MERCHANT_ID, keyId));
        verify(apiKeyCache, never()).evict(any());
    }

    @Test
    void revokeRejectsKeyOwnedByAnotherMerchant() {
        UUID keyId = UUID.randomUUID();
        ApiKey key = ApiKey.builder()
                .id(keyId)
                .keyId("np_test_other")
                .merchant(Merchant.builder().id(UUID.randomUUID()).build())
                .environment(Environment.TEST)
                .build();
        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));

        assertThrows(ResourceNotFoundException.class, () -> service.revoke(MERCHANT_ID, keyId));
        assertTrue(key.isEnabled());
        verify(apiKeyCache, never()).evict(any());
    }
}
