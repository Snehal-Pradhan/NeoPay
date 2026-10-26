package com.snehal.neopay.payment.client;

import com.snehal.neopay.common.dto.VaultChargeResponse;
import com.snehal.neopay.common.dto.VaultChargeRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "vault-service", path = "/internal/vault", url = "${VAULT_SERVICE_URI:}")
public interface VaultServiceClient {

    @PostMapping("/charge")
    VaultChargeResponse charge(@RequestBody VaultChargeRequest request);
}
