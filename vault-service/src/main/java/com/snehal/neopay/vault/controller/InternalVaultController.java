package com.snehal.neopay.vault.controller;

import com.snehal.neopay.common.dto.VaultChargeResponse;
import com.snehal.neopay.common.dto.VaultChargeRequest;
import com.snehal.neopay.vault.service.VaultService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/vault")
public class InternalVaultController {

    private final VaultService vaultService;

    @PostMapping("/charge")
    public VaultChargeResponse charge(@RequestBody VaultChargeRequest request) {
        return VaultChargeResponse.from(
                vaultService.charge(request.paymentId(), request.token(), request.amount(), request.methodDetails()));
    }
}
