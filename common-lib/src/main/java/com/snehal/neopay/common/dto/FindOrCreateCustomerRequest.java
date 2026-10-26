package com.snehal.neopay.common.dto;

import java.util.UUID;

public record FindOrCreateCustomerRequest(
        UUID merchantId,
        String email,
        String name,
        String phone
) {
}
