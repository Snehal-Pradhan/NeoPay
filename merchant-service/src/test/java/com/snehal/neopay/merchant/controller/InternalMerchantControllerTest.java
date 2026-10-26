package com.snehal.neopay.merchant.controller;

import com.snehal.neopay.common.dto.SettlementBankDetails;
import com.snehal.neopay.common.dto.WebhookTarget;
import com.snehal.neopay.merchant.api.MerchantLookupService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalMerchantControllerTest {

    private final MerchantLookupService lookupService = mock(MerchantLookupService.class);
    private final InternalMerchantController controller = new InternalMerchantController(lookupService);

    @Test
    void webhookTargetsDelegatesMerchantAndEvent() {
        UUID merchantId = UUID.randomUUID();
        WebhookTarget target = new WebhookTarget(UUID.randomUUID(),
                "https://example.com/hook", "secret");
        when(lookupService.getActiveConfigsForEvent(merchantId, "payment.captured"))
                .thenReturn(List.of(target));

        List<WebhookTarget> result = controller.getActiveConfigsForEvent(merchantId, "payment.captured");

        assertEquals(1, result.size());
        assertSame(target, result.get(0));
    }

    @Test
    void activeIdsDelegates() {
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(lookupService.listActiveMerchantIds()).thenReturn(ids);

        assertSame(ids, controller.listActiveMerchantIds());
    }

    @Test
    void settlementBankDetailsDelegates() {
        UUID merchantId = UUID.randomUUID();
        SettlementBankDetails details = new SettlementBankDetails("1234", "IFSC0001", "Holder");
        when(lookupService.getSettlementBankDetails(merchantId)).thenReturn(details);

        assertSame(details, controller.getSettlementBankDetails(merchantId));
    }
}
