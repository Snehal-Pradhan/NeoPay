package com.snehal.neopay.payment.controller;

import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.common.domain.PaymentId;
import com.snehal.neopay.common.dto.PaymentSettlementView;
import com.snehal.neopay.payment.api.PaymentLookupService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalSettlementControllerTest {

    private final PaymentLookupService lookupService = mock(PaymentLookupService.class);
    private final InternalSettlementController controller = new InternalSettlementController(lookupService);

    @Test
    void unsettledCapturedDelegatesMerchantId() {
        UUID merchantId = UUID.randomUUID();
        List<PaymentSettlementView> views = List.of();
        when(lookupService.findUnsettledCapturedPayments(MerchantId.of(merchantId))).thenReturn(views);

        assertSame(views, controller.findUnsettledCaptured(merchantId));
    }

    @Test
    void markSettledMapsIdsToPaymentIds() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        controller.markSettled(List.of(first, second));

        verify(lookupService).markSettled(List.of(PaymentId.of(first), PaymentId.of(second)));
    }
}
