package com.snehal.neopay.payment.dispute;

public enum DisputeStatus {
    OPENED,
    EVIDENCE_REQUIRED,
    UNDER_REVIEW,
    REPRESENTED,
    WON,
    LOST;

    public boolean isTerminal() {
        return this == WON || this == LOST;
    }
}
