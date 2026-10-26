package com.snehal.neopay.common.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentSettlementView(
        UUID paymentId,
        long amountUnits,
        long refundedAmountUnits,
        String currency,
        LocalDateTime capturedAt,
        long settlementAmountUnits,
        String settlementCurrency
) {
}
