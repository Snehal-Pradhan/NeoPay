package com.snehal.neopay.payment.risk;

import java.util.UUID;

public record RiskAuthorizeRequest(UUID merchantId, long amountUnits) {
}
