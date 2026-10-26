package com.snehal.neopay.common.iso20022;

public record StatementEntry(String reference, long amountUnits, String currency) {
}
