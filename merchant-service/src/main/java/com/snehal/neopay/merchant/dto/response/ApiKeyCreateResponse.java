package com.snehal.neopay.merchant.dto.response;


import com.snehal.neopay.common.enums.Environment;

import java.util.UUID;

public record ApiKeyCreateResponse(
        UUID id,
        String keyId,
        String keySecret,
        Environment environment
) {
}
