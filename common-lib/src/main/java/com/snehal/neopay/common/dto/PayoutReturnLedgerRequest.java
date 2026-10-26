package com.snehal.neopay.common.dto;

import java.util.UUID;

public record PayoutReturnLedgerRequest(UUID payoutId, long amountUnits, String currency) {
}
