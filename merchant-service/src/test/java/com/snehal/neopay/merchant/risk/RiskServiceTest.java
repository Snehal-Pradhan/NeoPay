package com.snehal.neopay.merchant.risk;

import com.snehal.neopay.common.enums.MerchantTier;

import com.snehal.neopay.common.enums.MerchantStatus;
import com.snehal.neopay.merchant.entity.Merchant;
import com.snehal.neopay.merchant.repository.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskServiceTest {

    private static final UUID MERCHANT = UUID.randomUUID();

    private MerchantRepository merchantRepository;
    private MerchantLimitUsageRepository usageRepository;
    private SanctionsScreener sanctions;
    private RiskService service;

    @BeforeEach
    void setUp() {
        merchantRepository = mock(MerchantRepository.class);
        usageRepository = mock(MerchantLimitUsageRepository.class);
        sanctions = mock(SanctionsScreener.class);
        service = new RiskService(merchantRepository, usageRepository, sanctions);
    }

    @Test
    void activeMerchantBelowLimitsIsAllowed() {
        merchant(MerchantStatus.ACTIVE, MerchantTier.TIER_1, "Acme");
        when(usageRepository.findByMerchantIdAndUsageDate(any(), any())).thenReturn(Optional.empty());
        when(usageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RiskDecision decision = service.authorize(MERCHANT, 50_000);

        assertTrue(decision.allowed());
        assertFalse(decision.stepUpRequired());
    }

    @Test
    void pendingKycIsRejected() {
        merchant(MerchantStatus.PENDING_KYC, MerchantTier.TIER_1, "Acme");

        RiskDecision decision = service.authorize(MERCHANT, 100);

        assertFalse(decision.allowed());
        assertEquals("KYC_NOT_VERIFIED", decision.reason());
    }

    @Test
    void sanctionsHitIsRejected() {
        merchant(MerchantStatus.ACTIVE, MerchantTier.TIER_1, "Acme");
        when(sanctions.isSanctioned("Acme")).thenReturn(true);

        RiskDecision decision = service.authorize(MERCHANT, 100);

        assertEquals("SANCTIONS_HIT", decision.reason());
    }

    @Test
    void perTransactionLimitIsEnforced() {
        merchant(MerchantStatus.ACTIVE, MerchantTier.TIER_1, "Acme");

        RiskDecision decision = service.authorize(MERCHANT, 100_001);

        assertEquals("LIMIT_EXCEEDED", decision.reason());
    }

    @Test
    void dailyLimitIsEnforced() {
        merchant(MerchantStatus.ACTIVE, MerchantTier.TIER_1, "Acme");
        MerchantLimitUsage usage = MerchantLimitUsage.builder()
                .merchantId(MERCHANT)
                .usageDate(java.time.LocalDate.now())
                .amountUnits(490_000)
                .requestCount(3)
                .build();
        when(usageRepository.findByMerchantIdAndUsageDate(any(), any()))
                .thenReturn(Optional.of(usage));

        RiskDecision decision = service.authorize(MERCHANT, 20_000);

        assertEquals("DAILY_LIMIT_EXCEEDED", decision.reason());
    }

    @Test
    void velocityThresholdRequiresStepUp() {
        merchant(MerchantStatus.ACTIVE, MerchantTier.TIER_1, "Acme");
        MerchantLimitUsage usage = MerchantLimitUsage.builder()
                .merchantId(MERCHANT)
                .usageDate(java.time.LocalDate.now())
                .amountUnits(10_000)
                .requestCount(5)
                .build();
        when(usageRepository.findByMerchantIdAndUsageDate(any(), any()))
                .thenReturn(Optional.of(usage));
        when(usageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RiskDecision decision = service.authorize(MERCHANT, 1_000);

        assertTrue(decision.allowed());
        assertTrue(decision.stepUpRequired());
        assertEquals("STEP_UP_REQUIRED", decision.reason());
    }

    @Test
    void nonPositiveAmountIsRejected() {
        merchant(MerchantStatus.ACTIVE, MerchantTier.TIER_1, "Acme");

        assertEquals("INVALID_AMOUNT", service.authorize(MERCHANT, 0).reason());
    }

    private void merchant(MerchantStatus status, MerchantTier tier, String name) {
        Merchant merchant = Merchant.builder()
                .id(MERCHANT)
                .name(name)
                .email("a@b.com")
                .status(status)
                .tier(tier)
                .build();
        when(merchantRepository.findById(MERCHANT)).thenReturn(Optional.of(merchant));
    }

    @Test
    void unknownMerchantRaisesNotFound() {
        when(merchantRepository.findById(MERCHANT)).thenReturn(Optional.empty());

        assertThrows(com.snehal.neopay.common.exception.ResourceNotFoundException.class,
                () -> service.authorize(MERCHANT, 100));
    }

    @Test
    void amountExactlyAtPerTransactionLimitIsAllowed() {
        merchant(MerchantStatus.ACTIVE, MerchantTier.TIER_1, "Acme");
        when(usageRepository.findByMerchantIdAndUsageDate(any(), any())).thenReturn(Optional.empty());
        when(usageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertTrue(service.authorize(MERCHANT, 100_000).allowed());
    }

    @Test
    void dailyLimitIsInclusiveOfTheExactRemainingAmount() {
        merchant(MerchantStatus.ACTIVE, MerchantTier.TIER_1, "Acme");
        MerchantLimitUsage usage = MerchantLimitUsage.builder()
                .merchantId(MERCHANT)
                .usageDate(java.time.LocalDate.now())
                .amountUnits(480_000)
                .requestCount(1)
                .build();
        when(usageRepository.findByMerchantIdAndUsageDate(any(), any()))
                .thenReturn(Optional.of(usage));
        when(usageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertTrue(service.authorize(MERCHANT, 20_000).allowed());
    }

    @Test
    void missingTierDefaultsToTierOne() {
        merchant(MerchantStatus.ACTIVE, null, "Acme");
        when(usageRepository.findByMerchantIdAndUsageDate(any(), any())).thenReturn(Optional.empty());
        when(usageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertTrue(service.authorize(MERCHANT, 50_000).allowed());
    }

    @Test
    void authorizeRecordsUsageAndPersistsIt() {
        merchant(MerchantStatus.ACTIVE, MerchantTier.TIER_1, "Acme");
        MerchantLimitUsage usage = MerchantLimitUsage.builder()
                .merchantId(MERCHANT)
                .usageDate(java.time.LocalDate.now())
                .amountUnits(1_000)
                .requestCount(2)
                .build();
        when(usageRepository.findByMerchantIdAndUsageDate(any(), any()))
                .thenReturn(Optional.of(usage));
        when(usageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.authorize(MERCHANT, 2_500);

        assertEquals(3_500, usage.getAmountUnits());
        assertEquals(3, usage.getRequestCount());
        verify(usageRepository).save(usage);
    }
}
