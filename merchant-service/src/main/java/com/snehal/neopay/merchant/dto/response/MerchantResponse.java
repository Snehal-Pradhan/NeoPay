package com.snehal.neopay.merchant.dto.response;


import com.snehal.neopay.common.enums.BusinessType;
import com.snehal.neopay.common.enums.MerchantStatus;

import java.util.UUID;

public record MerchantResponse(
        UUID id,
        String name,
        String email,
        String businessName,
        BusinessType businessType,
        MerchantStatus merchantStatus
) {
}
