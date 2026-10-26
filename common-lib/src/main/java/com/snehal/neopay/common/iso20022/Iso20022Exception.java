package com.snehal.neopay.common.iso20022;

public class Iso20022Exception extends RuntimeException {

    public Iso20022Exception(String message) {
        super(message);
    }

    public Iso20022Exception(String message, Throwable cause) {
        super(message, cause);
    }
}
