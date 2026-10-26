package com.snehal.neopay.payment.controller;

import com.snehal.neopay.common.context.MerchantContext;
import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.common.domain.PaymentId;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.payment.dto.request.CreateRefundRequest;
import com.snehal.neopay.payment.dto.response.RefundResponse;
import com.snehal.neopay.payment.service.RefundService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefundControllerTest {

    private final RefundService refundService = mock(RefundService.class);
    private final MerchantContext merchantContext = new MerchantContext();
    private final RefundController controller = new RefundController(refundService, merchantContext);

    @Test
    void createReturns201AndForwardsIdempotencyKey() {
        UUID merchantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        merchantContext.setMerchantId(merchantId);
        RefundResponse response = mock(RefundResponse.class);
        CreateRefundRequest request = new CreateRefundRequest(Money.inr(100), "customer request");
        when(refundService.create(MerchantId.of(merchantId), PaymentId.of(paymentId), request, "key-1"))
                .thenReturn(response);

        var result = controller.create(paymentId, request, "key-1");

        assertEquals(201, result.getStatusCode().value());
        assertSame(response, result.getBody());
    }

    @Test
    void listReturnsRefundsForTheCurrentMerchant() {
        UUID merchantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        merchantContext.setMerchantId(merchantId);
        List<RefundResponse> refunds = List.of(mock(RefundResponse.class));
        when(refundService.list(MerchantId.of(merchantId), PaymentId.of(paymentId))).thenReturn(refunds);

        var result = controller.list(paymentId);

        assertEquals(200, result.getStatusCode().value());
        assertSame(refunds, result.getBody());
        verify(refundService).list(eq(MerchantId.of(merchantId)), eq(PaymentId.of(paymentId)));
    }
}
