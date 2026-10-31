package com.snehal.neopay.operations.settlement;

import com.snehal.neopay.common.dto.PaymentSettlementView;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.money.PricingTerms;
import com.snehal.neopay.operations.entity.MerchantFeeSchedule;
import com.snehal.neopay.operations.entity.MerchantMethodPricing;
import com.snehal.neopay.operations.repository.MerchantFeeScheduleRepository;
import com.snehal.neopay.operations.repository.MerchantMethodPricingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettlementPricingTest {

    private static final UUID MERCHANT = UUID.randomUUID();

    private MerchantFeeScheduleRepository defaults;
    private MerchantMethodPricingRepository overrides;
    private SettlementFeeCalculator calculator;

    @BeforeEach
    void setUp() {
        defaults = mock(MerchantFeeScheduleRepository.class);
        overrides = mock(MerchantMethodPricingRepository.class);
        when(defaults.findById(MERCHANT)).thenReturn(Optional.empty());
        when(overrides.findApplicable(eq(MERCHANT), any())).thenReturn(List.of());
        calculator = new SettlementFeeCalculator(defaults, overrides);
    }

    @Test
    void noOverridesReproducesTheLegacyAggregate() {
        MerchantPricing pricing = calculator.pricingFor(MERCHANT, LocalDate.now());

        FeeBreakdown fees = calculator.breakdown(pricing, "INR",
                List.of(payment(10_000, "UPI"), payment(10_000, "CARD")));

        assertEquals(400, fees.fee().getAmountUnits());
        assertEquals(72, fees.gst().getAmountUnits());
        assertEquals(40, fees.tds().getAmountUnits());
        assertEquals(512, fees.totalUnits());
    }

    @Test
    void methodOverrideAppliesOnlyToThatMethod() {
        when(overrides.findApplicable(eq(MERCHANT), any())).thenReturn(List.of(
                override("CARD", 1_000, 0)));

        MerchantPricing pricing = calculator.pricingFor(MERCHANT, LocalDate.now());
        FeeBreakdown fees = calculator.breakdown(pricing, "INR",
                List.of(payment(10_000, "UPI"), payment(10_000, "CARD")));

        assertEquals(200 + 1_000, fees.fee().getAmountUnits());
        assertEquals(200, pricing.termsFor("UPI").fee(Money.inr(10_000)).getAmountUnits());
        assertEquals(1_000, pricing.termsFor("CARD").fee(Money.inr(10_000)).getAmountUnits());
    }

    @Test
    void unknownMethodFallsBackToDefault() {
        MerchantPricing pricing = new MerchantPricing(PricingTerms.standardIndia(),
                Map.of("CARD", new PricingTerms(1_000, 0, 1_800, 1_000, 500, 0,
                        java.math.RoundingMode.HALF_UP)));

        assertEquals(200, pricing.termsFor("NETBANKING").fee(Money.inr(10_000)).getAmountUnits());
        assertEquals(200, pricing.termsFor(null).fee(Money.inr(10_000)).getAmountUnits());
    }

    @Test
    void merchantDefaultScheduleIsUsedWhenPresent() {
        when(defaults.findById(MERCHANT)).thenReturn(Optional.of(MerchantFeeSchedule.builder()
                .merchantId(MERCHANT).feeBps(100).gstBps(1_800).tdsBps(0).reserveBps(0).build()));

        MerchantPricing pricing = calculator.pricingFor(MERCHANT, LocalDate.now());

        assertEquals(100, pricing.defaultTerms().feeBps());
        assertEquals(0, pricing.defaultTerms().tdsBps());
    }

    @Test
    void refundedVolumeDoesNotReduceGrossFee() {
        MerchantPricing pricing = MerchantPricing.standard();

        FeeBreakdown fees = calculator.breakdown(pricing, "INR",
                List.of(new PaymentSettlementView(UUID.randomUUID(), 10_000, 4_000, "INR",
                        LocalDateTime.now(), 6_000, "INR", "UPI")));

        assertEquals(120, fees.fee().getAmountUnits());
    }

    @Test
    void zeroGroupsProduceZeroBreakdown() {
        FeeBreakdown fees = calculator.breakdown(MerchantPricing.standard(), "INR", List.of());

        assertEquals(0, fees.totalUnits());
        assertTrue(fees.fee().getAmountUnits() == 0);
    }

    private static PaymentSettlementView payment(long amount, String method) {
        return new PaymentSettlementView(UUID.randomUUID(), amount, 0, "INR",
                LocalDateTime.now(), amount, "INR", method);
    }

    private static MerchantMethodPricing override(String method, int feeBps, long cap) {
        return MerchantMethodPricing.builder()
                .merchantId(MERCHANT)
                .paymentMethod(method)
                .feeBps(feeBps)
                .fixedFeeUnits(0)
                .gstBps(1_800)
                .tdsBps(1_000)
                .reserveBps(500)
                .maxFeeUnits(cap)
                .effectiveFrom(LocalDate.now().minusDays(1))
                .build();
    }
}
