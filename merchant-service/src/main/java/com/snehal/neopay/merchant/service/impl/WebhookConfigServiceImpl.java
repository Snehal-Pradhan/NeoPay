package com.snehal.neopay.merchant.service.impl;

import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.common.util.RandomizerUtil;
import com.snehal.neopay.merchant.dto.request.UpdateWebhookConfigRequest;
import com.snehal.neopay.merchant.dto.response.WebhookConfigResponse;
import com.snehal.neopay.merchant.entity.Merchant;
import com.snehal.neopay.merchant.entity.MerchantWebhookConfig;
import com.snehal.neopay.merchant.mapper.WebhookConfigMapper;
import com.snehal.neopay.merchant.repository.MerchantRepository;
import com.snehal.neopay.merchant.repository.WebhookConfigRepository;
import com.snehal.neopay.merchant.service.WebhookConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookConfigServiceImpl implements WebhookConfigService {

    private final MerchantRepository merchantRepository;
    private final WebhookConfigRepository merchantWebhookConfigRepository;
    private final BytesEncryptor bytesEncryptor;
    private final WebhookConfigMapper webhookConfigMapper;

    @Override
    public WebhookConfigResponse create(UUID merchantId, UpdateWebhookConfigRequest request) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", merchantId));

        String rawSecret = RandomizerUtil.randomBase64(32);
        byte[] rawSecretBytes = rawSecret.getBytes(StandardCharsets.UTF_8);

        String encryptedSecret = Base64.getEncoder().encodeToString
                (bytesEncryptor.encrypt(rawSecretBytes));

        MerchantWebhookConfig config = MerchantWebhookConfig.builder()
                .merchant(merchant)
                .targetUrl(request.targetUrl())
                .enabled(true)
                .eventTypes(request.eventTypes())
                .webhookSecret(encryptedSecret)
                .build();

        config = merchantWebhookConfigRepository.save(config);

        return webhookConfigMapper.toResponse(config, rawSecret);
    }

    @Override
    public Page<WebhookConfigResponse> list(UUID merchantId, Pageable pageable) {
        return merchantWebhookConfigRepository.findByMerchant_Id(merchantId, pageable)
                .map(config -> webhookConfigMapper.toResponse(config, null));
    }

    @Override
    public WebhookConfigResponse getById(UUID merchantId, UUID configId) {
        MerchantWebhookConfig config = requireOwnedConfig(merchantId, configId);
        return webhookConfigMapper.toResponse(config, null);
    }

    @Override
    @Transactional
    public WebhookConfigResponse update(UUID merchantId, UUID configId, UpdateWebhookConfigRequest request) {
        MerchantWebhookConfig config = requireOwnedConfig(merchantId, configId);
        config.setTargetUrl(request.targetUrl());
        config.setEventTypes(request.eventTypes());
        log.info("Merchant webhook config updated id={} merchantId={}", configId, merchantId);
        return webhookConfigMapper.toResponse(config, null);
    }

    @Override
    @Transactional
    public void delete(UUID merchantId, UUID configId) {
        MerchantWebhookConfig config = requireOwnedConfig(merchantId, configId);
        merchantWebhookConfigRepository.delete(config);
        log.info("Merchant webhook config deleted id={} merchantId={}", configId, merchantId);
    }

    private MerchantWebhookConfig requireOwnedConfig(UUID merchantId, UUID configId) {
        return merchantWebhookConfigRepository.findByIdAndMerchant_Id(configId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("MerchantWebhookConfig", configId));
    }
}
















