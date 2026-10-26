package com.snehal.neopay.operations.settlement;

import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.operations.entity.Settlement;
import com.snehal.neopay.operations.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Human-readable settlement statement derived from the stored settlement record.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/settlements")
public class SettlementStatementController {

    private final SettlementRepository settlementRepository;

    @GetMapping(value = "/{settlementId}/statement", produces = "text/csv")
    public String statement(@PathVariable UUID settlementId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement", settlementId));

        StringBuilder csv = new StringBuilder();
        csv.append("field,amountUnits,currency\n");
        append(csv, "gross", settlement.getGrossAmount());
        append(csv, "refund", settlement.getRefundAmount());
        append(csv, "fee", settlement.getFeeAmount());
        append(csv, "gst", settlement.getGstAmount());
        append(csv, "tds", settlement.getTdsAmount());
        append(csv, "reserve", settlement.getReserveAmount());
        append(csv, "carryForward", settlement.getCarryForwardAmount());
        append(csv, "net", settlement.getNetAmount());
        return csv.toString();
    }

    private static void append(StringBuilder csv, String field,
                               com.snehal.neopay.common.entity.Money amount) {
        if (amount == null) {
            return;
        }
        csv.append(field).append(',')
                .append(amount.getAmountUnits()).append(',')
                .append(amount.getCurrency()).append('\n');
    }
}
