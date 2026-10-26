package com.snehal.neopay.payment.risk;

public record RiskDecisionResponse(boolean allowed, boolean stepUpRequired, String reason) {
}
