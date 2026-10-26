package com.snehal.neopay.vault.controller;

import com.snehal.neopay.common.dto.PaymentProcessorResponse;
import com.snehal.neopay.common.dto.VaultChargeRequest;
import com.snehal.neopay.common.dto.VaultChargeResponse;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.vault.service.VaultService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalVaultControllerTest {

    private final VaultService vaultService = mock(VaultService.class);
    private final InternalVaultController controller = new InternalVaultController(vaultService);

    @Test
    void pendingResponseIsMappedForTheCaller() {
        when(vaultService.charge(any(), any(), any(), any()))
                .thenReturn(new PaymentProcessorResponse.Pending("proc-1"));

        VaultChargeResponse response = controller.charge(
                new VaultChargeRequest(UUID.randomUUID(), "tok_1", Money.inr(500), Map.of()));

        assertEquals("pending", response.type());
        assertEquals("proc-1", response.processorReference());
    }

    @Test
    void failureResponseCarriesErrorDetails() {
        when(vaultService.charge(any(), any(), any(), any()))
                .thenReturn(new PaymentProcessorResponse.Failure("CARD_DECLINED", "declined"));

        VaultChargeResponse response = controller.charge(
                new VaultChargeRequest(UUID.randomUUID(), "tok_1", Money.inr(500), Map.of()));

        assertEquals("failure", response.type());
        assertEquals("CARD_DECLINED", response.errorCode());
        assertEquals("declined", response.errorDescription());
    }

    @Test
    void successResponseCarriesBothReferences() {
        when(vaultService.charge(any(), any(), any(), any()))
                .thenReturn(new PaymentProcessorResponse.Success("proc-2", "bank-2"));

        VaultChargeResponse response = controller.charge(
                new VaultChargeRequest(UUID.randomUUID(), "tok_1", Money.inr(500), Map.of()));

        assertEquals("success", response.type());
        assertEquals("proc-2", response.processorReference());
        assertEquals("bank-2", response.bankReference());
    }
}
