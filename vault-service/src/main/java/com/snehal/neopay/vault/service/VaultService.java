package com.snehal.neopay.vault.service;


import com.snehal.neopay.common.dto.PaymentProcessorResponse;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.vault.dto.request.TokenizeRequest;
import com.snehal.neopay.vault.dto.response.TokenizeResponse;

import java.util.Map;
import java.util.UUID;

public interface VaultService {

    TokenizeResponse tokenize(TokenizeRequest request, UUID merchantId);

    PaymentProcessorResponse charge(UUID paymentId, String token, Money amount, Map<String, Object> methodDetails);
}
