package com.snehal.neopay.common.iso20022;

public record PayoutInstruction(String endToEndId, long amountUnits, String currency,
                                String creditorName) {
}
