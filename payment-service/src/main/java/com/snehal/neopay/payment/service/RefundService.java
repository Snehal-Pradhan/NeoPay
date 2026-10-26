package com.snehal.neopay.payment.service;

import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.common.domain.PaymentId;
import com.snehal.neopay.payment.dto.request.CreateRefundRequest;
import com.snehal.neopay.payment.dto.response.RefundResponse;

import java.util.List;

public interface RefundService {

    RefundResponse create(MerchantId merchantId, PaymentId paymentId,
                          CreateRefundRequest request, String idempotencyKey);

    List<RefundResponse> list(MerchantId merchantId, PaymentId paymentId);
}
