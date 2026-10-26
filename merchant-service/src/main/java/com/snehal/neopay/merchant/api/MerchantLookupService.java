package com.snehal.neopay.merchant.api;

import com.snehal.neopay.common.dto.SettlementBankDetails;
import com.snehal.neopay.common.dto.WebhookTarget;

import java.util.List;
import java.util.UUID;

public interface MerchantLookupService {

    List<WebhookTarget> getActiveConfigsForEvent(UUID merchantId, String eventType);

    List<UUID> listActiveMerchantIds();

    SettlementBankDetails getSettlementBankDetails(UUID merchantId);

}
