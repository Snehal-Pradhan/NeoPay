package com.snehal.neopay.common.exception;

import lombok.Getter;

@Getter
public class RiskRejectedException extends RuntimeException {

    private final String reason;

    public RiskRejectedException(String reason) {
        super("Payment rejected by risk controls: " + reason);
        this.reason = reason;
    }
}
