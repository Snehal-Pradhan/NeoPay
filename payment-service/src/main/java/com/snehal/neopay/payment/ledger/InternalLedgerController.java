package com.snehal.neopay.payment.ledger;

import com.snehal.neopay.common.dto.PayoutReturnLedgerRequest;
import com.snehal.neopay.common.dto.SettlementLedgerRequest;
import com.snehal.neopay.common.entity.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/ledger")
public class InternalLedgerController {

    private final LedgerService ledgerService;

    @PostMapping("/settlement")
    public void postSettlement(@RequestBody SettlementLedgerRequest request) {
        ledgerService.postSettlement(request);
    }

    @PostMapping("/payout-return")
    public void postPayoutReturn(@RequestBody PayoutReturnLedgerRequest request) {
        ledgerService.postPayoutReturn(request.payoutId(),
                Money.of(Math.toIntExact(request.amountUnits()), request.currency()));
    }
}
