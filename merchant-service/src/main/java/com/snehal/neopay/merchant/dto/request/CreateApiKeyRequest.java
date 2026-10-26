package com.snehal.neopay.merchant.dto.request;


import com.snehal.neopay.common.enums.Environment;

public record CreateApiKeyRequest(
        Environment environment
) {
}
