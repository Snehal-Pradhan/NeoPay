package com.snehal.neopay.common.exception;

public class StepUpRequiredException extends RuntimeException {

    public StepUpRequiredException() {
        super("Additional authentication is required for this payment");
    }
}
