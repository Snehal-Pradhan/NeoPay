package com.snehal.neopay.payment.controller;

import com.snehal.neopay.common.context.MerchantContext;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.PaymentMethod;
import com.snehal.neopay.payment.dto.request.CreateOrderRequest;
import com.snehal.neopay.payment.dto.request.PaymentInitRequest;
import com.snehal.neopay.payment.dto.response.OrderResponse;
import com.snehal.neopay.payment.dto.response.PaymentResponse;
import com.snehal.neopay.payment.service.OrderService;
import com.snehal.neopay.payment.service.PaymentService;
import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.common.domain.PaymentId;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentControllersTest {

    private final UUID merchantId = UUID.randomUUID();

    @Test
    void orderCreateReturns201() {
        OrderService service = mock(OrderService.class);
        MerchantContext context = new MerchantContext();
        context.setMerchantId(merchantId);
        OrderController controller = new OrderController(context, service);

        CreateOrderRequest request = new CreateOrderRequest(Money.inr(100), "r-1", Map.of(), null, null);
        OrderResponse response = mock(OrderResponse.class);
        when(service.create(MerchantId.of(merchantId), request)).thenReturn(response);

        var result = controller.create(request);

        assertEquals(201, result.getStatusCode().value());
        assertEquals(response, result.getBody());
    }

    @Test
    void paymentInitiateReturns201AndForwardsIdempotencyKey() {
        PaymentService service = mock(PaymentService.class);
        MerchantContext context = new MerchantContext();
        context.setMerchantId(merchantId);
        PaymentController controller = new PaymentController(service, context);

        PaymentInitRequest request = new PaymentInitRequest(UUID.randomUUID(), PaymentMethod.UPI, Map.of());
        PaymentResponse response = mock(PaymentResponse.class);
        when(service.initiate(MerchantId.of(merchantId), request, "key-1")).thenReturn(response);

        var result = controller.initiate(request, "key-1");

        assertEquals(201, result.getStatusCode().value());
        assertEquals(response, result.getBody());
    }

    @Test
    void paymentCaptureReturns200() {
        PaymentService service = mock(PaymentService.class);
        MerchantContext context = new MerchantContext();
        context.setMerchantId(merchantId);
        PaymentController controller = new PaymentController(service, context);

        UUID paymentId = UUID.randomUUID();
        PaymentResponse response = mock(PaymentResponse.class);
        when(service.capture(MerchantId.of(merchantId), PaymentId.of(paymentId))).thenReturn(response);

        var result = controller.capture(paymentId);

        assertEquals(200, result.getStatusCode().value());
        assertEquals(response, result.getBody());
    }
}
