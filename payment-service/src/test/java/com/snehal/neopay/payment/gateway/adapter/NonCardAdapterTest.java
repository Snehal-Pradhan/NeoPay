package com.snehal.neopay.payment.gateway.adapter;

import com.snehal.neopay.common.dto.PaymentProcessorResponse;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.PaymentMethod;
import com.snehal.neopay.payment.gateway.dto.PaymentRequest;
import com.snehal.neopay.payment.gateway.dto.PaymentResult;
import com.snehal.neopay.payment.processor.PaymentProcessorRouter;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NonCardAdapterTest {

    private final PaymentProcessorRouter router = mock(PaymentProcessorRouter.class);

    @Test
    void upiPendingMapsToPending() {
        when(router.charge(any())).thenReturn(new PaymentProcessorResponse.Pending("UPI_REF"));

        PaymentResult result = new UpiPaymentAdapter(router).initiate(request(PaymentMethod.UPI));

        assertInstanceOf(PaymentResult.Pending.class, result);
    }

    @Test
    void upiFailureMapsError() {
        when(router.charge(any())).thenReturn(new PaymentProcessorResponse.Failure("UPI_DECLINED", "no"));

        PaymentResult result = new UpiPaymentAdapter(router).initiate(request(PaymentMethod.UPI));

        assertEquals("UPI_DECLINED", ((PaymentResult.Failure) result).errorCode());
    }

    @Test
    void upiSuccessMapsBankReference() {
        when(router.charge(any())).thenReturn(new PaymentProcessorResponse.Success("p", "bank"));

        PaymentResult result = new UpiPaymentAdapter(router).initiate(request(PaymentMethod.UPI));

        assertEquals("bank", ((PaymentResult.Success) result).bankReference());
    }

    @Test
    void upiExceptionMapsToFailure() {
        when(router.charge(any())).thenThrow(new IllegalStateException("processor down"));

        PaymentResult result = new UpiPaymentAdapter(router).initiate(request(PaymentMethod.UPI));

        assertEquals("UPI_FAILED", ((PaymentResult.Failure) result).errorCode());
    }

    @Test
    void netBankingPendingMapsToPending() {
        when(router.charge(any())).thenReturn(new PaymentProcessorResponse.Pending("NBK_REF"));

        PaymentResult result = new NetBankingAdapter(router).initiate(netBankingRequest());

        assertInstanceOf(PaymentResult.Pending.class, result);
    }

    @Test
    void netBankingExceptionMapsToFailure() {
        when(router.charge(any())).thenThrow(new IllegalStateException("down"));

        PaymentResult result = new NetBankingAdapter(router).initiate(netBankingRequest());

        assertEquals("NBK_FAILED", ((PaymentResult.Failure) result).errorCode());
    }

    @Test
    void captureReturnsSuccess() {
        assertEquals("UPI_REF", ((PaymentResult.Success)
                new UpiPaymentAdapter(router).capture(UUID.randomUUID())).bankReference());
        assertEquals("NBK_REF", ((PaymentResult.Success)
                new NetBankingAdapter(router).capture(UUID.randomUUID())).bankReference());
    }

    private static PaymentRequest netBankingRequest() {
        return new PaymentRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Money.inr(100), PaymentMethod.NETBANKING, Map.of("bank", "HDFC"));
    }

    private static PaymentRequest request(PaymentMethod method) {
        return new PaymentRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Money.inr(100), method, Map.of("vpa", "success@okaxis"));
    }

    @Test
    void netBankingWithoutBankDetailsIsRejectedBeforeCallingTheProcessor() {
        PaymentRequest missingBank = new PaymentRequest(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Money.inr(100), PaymentMethod.NETBANKING, Map.of());

        PaymentResult result = new NetBankingAdapter(router).initiate(missingBank);

        assertEquals("INVALID_METHOD_DETAILS", ((PaymentResult.Failure) result).errorCode());
        org.mockito.Mockito.verifyNoInteractions(router);
    }
}
