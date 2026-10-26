package com.snehal.neopay.merchant.controller;

import com.snehal.neopay.common.context.MerchantContext;
import com.snehal.neopay.merchant.dto.request.UpdateWebhookConfigRequest;
import com.snehal.neopay.merchant.dto.response.WebhookConfigResponse;
import com.snehal.neopay.merchant.service.WebhookConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/merchants/webhooks")
@RequiredArgsConstructor
public class WebhookConfigController {

    private final WebhookConfigService webhookConfigService;
    private final MerchantContext merchantContext;

    @PostMapping
    public ResponseEntity<WebhookConfigResponse> create(@Valid @RequestBody UpdateWebhookConfigRequest request) {
        return ResponseEntity.ok(webhookConfigService.create(merchantContext.getMerchantId(), request));
    }

    @GetMapping
    public ResponseEntity<Page<WebhookConfigResponse>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(webhookConfigService.list(merchantContext.getMerchantId(), pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WebhookConfigResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(webhookConfigService.getById(merchantContext.getMerchantId(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WebhookConfigResponse> update(@PathVariable UUID id,
                                                        @Valid @RequestBody UpdateWebhookConfigRequest request) {
        return ResponseEntity.ok(webhookConfigService.update(merchantContext.getMerchantId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        webhookConfigService.delete(merchantContext.getMerchantId(), id);
        return ResponseEntity.noContent().build();
    }

}
