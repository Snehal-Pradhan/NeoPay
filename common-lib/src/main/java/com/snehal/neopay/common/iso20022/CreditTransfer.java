package com.snehal.neopay.common.iso20022;

public record CreditTransfer(
        String endToEndId,
        long amountUnits,
        String currency,
        String debtorName,
        String creditorName
) {
}
