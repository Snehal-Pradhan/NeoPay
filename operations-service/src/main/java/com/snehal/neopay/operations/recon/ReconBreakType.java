package com.snehal.neopay.operations.recon;

public enum ReconBreakType {
    MISSING_INTERNAL,
    MISSING_PSP,
    MISSING_BANK,
    AMOUNT_MISMATCH_PSP,
    AMOUNT_MISMATCH_BANK,
    CURRENCY_MISMATCH,
    DUPLICATE
}
