package com.snehal.neopay.payment.api;

import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.common.domain.PaymentId;
import com.snehal.neopay.common.dto.PaymentSettlementView;

import java.util.List;

public interface PaymentLookupService {

    List<PaymentSettlementView> findUnsettledCapturedPayments(MerchantId merchantId);

    void markSettled(List<PaymentId> paymentList);
}
