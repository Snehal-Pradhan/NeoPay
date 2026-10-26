package com.snehal.neopay.payment.service;

import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.common.domain.OrderId;
import com.snehal.neopay.payment.dto.request.CreateOrderRequest;
import com.snehal.neopay.payment.dto.response.OrderResponse;
import com.snehal.neopay.payment.dto.response.PaymentResponse;

import java.util.List;

public interface OrderService {
    OrderResponse create(MerchantId merchantId, CreateOrderRequest request);

    OrderResponse getById(MerchantId merchantId, OrderId orderId);

    OrderResponse cancel(MerchantId merchantId, OrderId orderId);

    List<PaymentResponse> listPayments(MerchantId merchantId, OrderId orderId);
}
