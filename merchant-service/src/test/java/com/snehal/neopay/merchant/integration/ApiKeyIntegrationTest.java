package com.snehal.neopay.merchant.integration;

import com.snehal.neopay.common.cache.ApiKeyCacheEntry;
import com.snehal.neopay.common.enums.BusinessType;
import com.snehal.neopay.common.enums.Environment;
import com.snehal.neopay.common.enums.MerchantStatus;
import com.snehal.neopay.merchant.dto.request.CreateApiKeyRequest;
import com.snehal.neopay.merchant.dto.response.ApiKeyCreateResponse;
import com.snehal.neopay.merchant.entity.Merchant;
import com.snehal.neopay.merchant.repository.MerchantRepository;
import com.snehal.neopay.merchant.service.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class ApiKeyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "?sslmode=disable");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private MerchantRepository merchantRepository;

    @Test
    void createPersistAndLookupRoundTrip() {
        UUID merchantId = persistMerchant("roundtrip@example.com");

        ApiKeyCreateResponse created = apiKeyService.create(merchantId,
                new CreateApiKeyRequest(Environment.TEST));

        ApiKeyCacheEntry entry = apiKeyService.findCacheEntry(created.keyId());

        assertEquals(created.keyId(), entry.keyId());
        assertEquals(merchantId, entry.merchantId());
        assertTrue(entry.enabled());
        assertNotEquals(created.keySecret(), entry.keySecretHash());
    }

    @Test
    void revokePersistsDisabledState() {
        UUID merchantId = persistMerchant("revoke@example.com");
        ApiKeyCreateResponse created = apiKeyService.create(merchantId,
                new CreateApiKeyRequest(Environment.TEST));

        apiKeyService.revoke(merchantId, created.id());

        assertFalse(apiKeyService.findCacheEntry(created.keyId()).enabled());
    }

    @Test
    void rotateKeepsOldSecretValidDuringGracePeriod() {
        UUID merchantId = persistMerchant("rotate@example.com");
        ApiKeyCreateResponse created = apiKeyService.create(merchantId,
                new CreateApiKeyRequest(Environment.TEST));

        ApiKeyCreateResponse rotated = apiKeyService.rotate(merchantId, created.id());

        ApiKeyCacheEntry entry = apiKeyService.findCacheEntry(created.keyId());
        assertNotEquals(created.keySecret(), rotated.keySecret());
        assertNotEquals(rotated.keySecret(), entry.keySecretHash());
        assertTrue(entry.gracePeriodExpiresAt() != null);
    }

    private UUID persistMerchant(String email) {
        Merchant merchant = Merchant.builder()
                .name("Integration Merchant")
                .email(email)
                .businessType(BusinessType.LLP)
                .status(MerchantStatus.ACTIVE)
                .build();
        return merchantRepository.save(merchant).getId();
    }
}
