package com.snehal.neopay.vault.controller;

import com.snehal.neopay.common.context.MerchantContext;
import com.snehal.neopay.common.enums.CardBrand;
import com.snehal.neopay.vault.dto.request.TokenizeRequest;
import com.snehal.neopay.vault.dto.response.TokenizeResponse;
import com.snehal.neopay.vault.service.VaultService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VaultControllerTest {

    @Test
    void tokenizeReturns201() {
        VaultService service = mock(VaultService.class);
        UUID merchantId = UUID.randomUUID();
        MerchantContext context = new MerchantContext();
        context.setMerchantId(merchantId);
        VaultController controller = new VaultController(service, context);

        TokenizeRequest request = new TokenizeRequest("4111111111111111", "123", 12, 2030, null, "Test");
        TokenizeResponse response = new TokenizeResponse("tok_1", "1111", CardBrand.VISA, 12, 2030);
        when(service.tokenize(request, merchantId)).thenReturn(response);

        var result = controller.tokenize(request);

        assertEquals(201, result.getStatusCode().value());
        assertEquals("tok_1", result.getBody().token());
    }
}
