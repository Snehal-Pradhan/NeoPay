package com.snehal.neopay.merchant.service.impl;

import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.merchant.dto.request.UpdateWebhookConfigRequest;
import com.snehal.neopay.merchant.dto.response.WebhookConfigResponse;
import com.snehal.neopay.merchant.entity.Merchant;
import com.snehal.neopay.merchant.entity.MerchantWebhookConfig;
import com.snehal.neopay.merchant.mapper.WebhookConfigMapper;
import com.snehal.neopay.merchant.repository.MerchantRepository;
import com.snehal.neopay.merchant.repository.WebhookConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.encrypt.BytesEncryptor;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookConfigServiceImplTest {

    private final MerchantRepository merchantRepository = mock(MerchantRepository.class);
    private final WebhookConfigRepository configRepository = mock(WebhookConfigRepository.class);
    private final BytesEncryptor encryptor = mock(BytesEncryptor.class);
    private final WebhookConfigMapper mapper = mock(WebhookConfigMapper.class);
    private final WebhookConfigServiceImpl service = new WebhookConfigServiceImpl(
            merchantRepository, configRepository, encryptor, mapper);

    @Test
    void createRejectsUnknownMerchant() {
        UUID merchantId = UUID.randomUUID();
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.create(merchantId, new UpdateWebhookConfigRequest("https://x.test/h", "ALL")));
    }

    @Test
    void createEncryptsSecretAndStoresEnabledConfig() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = Merchant.builder().id(merchantId).build();
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(encryptor.encrypt(any())).thenReturn("cipher".getBytes(StandardCharsets.UTF_8));
        when(configRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(), any())).thenReturn(mock(WebhookConfigResponse.class));

        service.create(merchantId, new UpdateWebhookConfigRequest("https://x.test/h", "ALL"));

        verify(configRepository).save(org.mockito.ArgumentMatchers.argThat(config ->
                config.getMerchant() == merchant
                        && Boolean.TRUE.equals(config.getEnabled())
                        && "https://x.test/h".equals(config.getTargetUrl())
                        && config.getWebhookSecret() != null));
    }

    @Test
    void updateReplacesUrlAndEventTypes() {
        UUID merchantId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        MerchantWebhookConfig config = MerchantWebhookConfig.builder()
                .id(configId)
                .targetUrl("https://old.test/h")
                .eventTypes("payment.captured")
                .build();
        when(configRepository.findByIdAndMerchant_Id(configId, merchantId))
                .thenReturn(Optional.of(config));
        when(mapper.toResponse(eq(config), any())).thenReturn(mock(WebhookConfigResponse.class));

        service.update(merchantId, configId,
                new UpdateWebhookConfigRequest("https://new.test/h", "refund.created"));

        assertEquals("https://new.test/h", config.getTargetUrl());
        assertEquals("refund.created", config.getEventTypes());
    }

    @Test
    void updateRejectsConfigOwnedByAnotherMerchant() {
        UUID merchantId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        when(configRepository.findByIdAndMerchant_Id(configId, merchantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.update(merchantId, configId,
                        new UpdateWebhookConfigRequest("https://new.test/h", "ALL")));
    }

    @Test
    void defaultsRemainSaneWhenEventTypesAreAbsent() {
        MerchantWebhookConfig config = MerchantWebhookConfig.builder()
                .targetUrl("https://x.test/h")
                .build();

        assertTrue(config.isSubscribedTo("any.event"));
        assertTrue(config.getEnabled());
    }
}
