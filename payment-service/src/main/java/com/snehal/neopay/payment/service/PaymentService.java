package com.snehal.neopay.payment.service;

import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.common.domain.PaymentId;
import com.snehal.neopay.payment.dto.request.PaymentInitRequest;
import com.snehal.neopay.payment.dto.response.PaymentResponse;

public interface PaymentService {

    PaymentResponse initiate(MerchantId merchantId, PaymentInitRequest request, String idempotencyKey);

    PaymentResponse capture(MerchantId merchantId, PaymentId paymentId);

    void resolveAuthorization(PaymentId paymentId, boolean approve,
                              String bankRef, String errorCode, String errorDescription);
}
