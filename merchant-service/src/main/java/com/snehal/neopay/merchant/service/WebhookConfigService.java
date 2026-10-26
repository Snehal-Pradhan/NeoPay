package com.snehal.neopay.merchant.service;

import com.snehal.neopay.merchant.dto.request.UpdateWebhookConfigRequest;
import com.snehal.neopay.merchant.dto.response.WebhookConfigResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface WebhookConfigService {

    WebhookConfigResponse create(UUID merchantId, UpdateWebhookConfigRequest request);

    Page<WebhookConfigResponse> list(UUID merchantId, Pageable pageable);

    WebhookConfigResponse getById(UUID merchantId, UUID configId);

    WebhookConfigResponse update(UUID merchantId, UUID configId, UpdateWebhookConfigRequest request);

    void delete(UUID merchantId, UUID configId);

}
