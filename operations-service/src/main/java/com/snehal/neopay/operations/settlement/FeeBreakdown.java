package com.snehal.neopay.operations.settlement;

import com.snehal.neopay.common.entity.Money;

/**
 * Aggregated processing deductions for one merchant and date. Reserve is merchant-level
 * risk, not a per-method fee, so it is computed by the executor on the post-debt base.
 */
public record FeeBreakdown(Money fee, Money gst, Money tds) {

    public long totalUnits() {
        return fee.getAmountUnits() + gst.getAmountUnits() + tds.getAmountUnits();
    }
}
