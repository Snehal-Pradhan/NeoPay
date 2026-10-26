package com.snehal.neopay.operations.payout;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal")
public class PayoutController {

    private final PayoutService payoutService;

    @GetMapping(value = "/settlements/{settlementId}/payout-file", produces = "text/csv")
    public String payoutFile(@PathVariable UUID settlementId) {
        return payoutService.payoutFile(settlementId);
    }

    @PostMapping("/payouts/{payoutId}/return")
    public Payout markReturned(@PathVariable UUID payoutId,
                               @RequestBody ReturnRequest request) {
        return payoutService.markReturned(payoutId, request.reason());
    }

    public record ReturnRequest(String reason) {
    }
}
