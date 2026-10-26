package com.snehal.neopay.operations.settlement;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.operations.settlement.dto.BankTransferResult;

import java.util.UUID;

public interface BankTransferProcessor {

    BankTransferResult initiate(UUID settlementId, UUID merchantId, Money amount,
                                String bankAccount, String ifsc);
}
