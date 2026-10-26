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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KycServiceTest {

    private static final UUID MERCHANT = UUID.randomUUID();

    private MerchantRepository repository;
    private KycService service;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        repository = mock(MerchantRepository.class);
        service = new KycService(repository);
        merchant = Merchant.builder()
                .id(MERCHANT)
                .name("Acme")
                .email("a@b.com")
                .status(MerchantStatus.PENDING_KYC)
                .tier(MerchantTier.TIER_1)
                .build();
        when(repository.findById(MERCHANT)).thenReturn(Optional.of(merchant));
        when(repository.save(any(Merchant.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void verifyActivatesMerchant() {
        assertEquals(MerchantStatus.ACTIVE, service.verify(MERCHANT));
    }

    @Test
    void rejectSuspendsMerchant() {
        assertEquals(MerchantStatus.SUSPENDED, service.reject(MERCHANT));
    }

    @Test
    void tierUpgradeIsPersisted() {
        assertEquals(MerchantTier.TIER_3, service.upgradeTier(MERCHANT, MerchantTier.TIER_3));
        assertEquals(MerchantTier.TIER_3, merchant.getTier());
    }

    @Test
    void statusIsReadable() {
        assertEquals(MerchantStatus.PENDING_KYC, service.status(MERCHANT));
    }

    @Test
    void statusForUnknownMerchantRaisesNotFound() {
        UUID unknown = UUID.randomUUID();
        when(repository.findById(unknown)).thenReturn(Optional.empty());

        com.snehal.neopay.common.exception.ResourceNotFoundException error =
                org.junit.jupiter.api.Assertions.assertThrows(
                        com.snehal.neopay.common.exception.ResourceNotFoundException.class,
                        () -> service.status(unknown));

        org.junit.jupiter.api.Assertions.assertEquals("Merchant", error.getResourceName());
    }
}
