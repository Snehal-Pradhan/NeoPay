package com.snehal.neopay.merchant.risk;

import com.snehal.neopay.common.enums.MerchantStatus;
import com.snehal.neopay.common.enums.MerchantTier;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskControllerTest {

    private final RiskService riskService = mock(RiskService.class);
    private final KycService kycService = mock(KycService.class);
    private final RiskController controller = new RiskController(riskService, kycService);

    @Test
    void authorizeDelegatesMerchantAndAmount() {
        UUID merchantId = UUID.randomUUID();
        RiskDecision decision = RiskDecision.permit();
        when(riskService.authorize(merchantId, 2_500L)).thenReturn(decision);

        RiskDecision result = controller.authorize(new RiskController.AuthorizeRequest(merchantId, 2_500L));

        assertSame(decision, result);
        verify(riskService).authorize(merchantId, 2_500L);
    }

    @Test
    void verifyKycDelegatesToService() {
        UUID merchantId = UUID.randomUUID();
        when(kycService.verify(merchantId)).thenReturn(MerchantStatus.ACTIVE);

        assertEquals(MerchantStatus.ACTIVE, controller.verify(merchantId));
    }

    @Test
    void rejectKycDelegatesToService() {
        UUID merchantId = UUID.randomUUID();
        when(kycService.reject(merchantId)).thenReturn(MerchantStatus.SUSPENDED);

        assertEquals(MerchantStatus.SUSPENDED, controller.reject(merchantId));
    }

    @Test
    void upgradeTierDelegatesTierValue() {
        UUID merchantId = UUID.randomUUID();
        when(kycService.upgradeTier(merchantId, MerchantTier.TIER_2)).thenReturn(MerchantTier.TIER_2);

        assertEquals(MerchantTier.TIER_2,
                controller.upgradeTier(merchantId, new RiskController.TierRequest(MerchantTier.TIER_2)));
    }
}
