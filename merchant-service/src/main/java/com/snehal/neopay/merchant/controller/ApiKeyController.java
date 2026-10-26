package com.snehal.neopay.merchant.controller;

import com.snehal.neopay.common.context.MerchantContext;
import com.snehal.neopay.merchant.dto.request.CreateApiKeyRequest;
import com.snehal.neopay.merchant.dto.response.ApiKeyCreateResponse;
import com.snehal.neopay.merchant.dto.response.ApiKeyResponse;
import com.snehal.neopay.merchant.service.ApiKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/merchants/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final MerchantContext merchantContext;

    @PostMapping
    public ResponseEntity<ApiKeyCreateResponse> create(@Valid @RequestBody CreateApiKeyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(apiKeyService.create(merchantContext.getMerchantId(), request));
    }

    @GetMapping
    public ResponseEntity<Page<ApiKeyResponse>> listByMerchant(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(apiKeyService.listByMerchant(merchantContext.getMerchantId(), pageable));
    }

    @DeleteMapping("/{keyId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID keyId) {
        apiKeyService.revoke(merchantContext.getMerchantId(), keyId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{keyId}/rotate")
    public ResponseEntity<ApiKeyCreateResponse> rotateKey( @PathVariable UUID keyId) {
        return ResponseEntity.ok(apiKeyService.rotate(merchantContext.getMerchantId(), keyId));
    }


}
