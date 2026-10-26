package com.snehal.neopay.vault.dto.response;


import com.snehal.neopay.common.enums.CardBrand;

public record TokenizeResponse(
        String token,
        String lastFour,
        CardBrand brand,
        Integer expiryMonth,
        Integer expiryYear
) {
}
