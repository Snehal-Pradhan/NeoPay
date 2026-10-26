package com.snehal.neopay.merchant.service.impl;

import com.snehal.neopay.common.dto.SettlementBankDetails;
import com.snehal.neopay.common.dto.WebhookTarget;
import com.snehal.neopay.common.enums.MerchantStatus;
import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.merchant.entity.Merchant;
import com.snehal.neopay.merchant.entity.MerchantWebhookConfig;
import com.snehal.neopay.merchant.repository.MerchantRepository;
import com.snehal.neopay.merchant.repository.WebhookConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.encrypt.BytesEncryptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MerchantLookupServiceImplTest {

    private final MerchantRepository merchantRepository = mock(MerchantRepository.class);
    private final WebhookConfigRepository webhookConfigRepository = mock(WebhookConfigRepository.class);
    private final BytesEncryptor encryptor = mock(BytesEncryptor.class);
    private final MerchantLookupServiceImpl service =
            new MerchantLookupServiceImpl(merchantRepository, webhookConfigRepository, encryptor);

    private static MerchantWebhookConfig config(UUID id, String url, String secretCipher, String events) {
        return MerchantWebhookConfig.builder()
                .id(id)
                .targetUrl(url)
                .webhookSecret(secretCipher)
                .eventTypes(events)
                .build();
    }

    @Test
    void decryptsSecretAndKeepsSubscribedConfigs() {
        UUID merchantId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        String cipher = Base64.getEncoder().encodeToString("cipher-bytes".getBytes(StandardCharsets.UTF_8));

        when(webhookConfigRepository.findByMerchant_IdAndEnabledTrue(merchantId))
                .thenReturn(List.of(config(configId, "https://a.example/hook", cipher, "payment.captured")));
        when(encryptor.decrypt("cipher-bytes".getBytes(StandardCharsets.UTF_8)))
                .thenReturn("plain-secret".getBytes(StandardCharsets.UTF_8));

        List<WebhookTarget> targets = service.getActiveConfigsForEvent(merchantId, "payment.captured");

        assertEquals(1, targets.size());
        assertEquals(configId, targets.get(0).configId());
        assertEquals("https://a.example/hook", targets.get(0).targetUrl());
        assertEquals("plain-secret", targets.get(0).webhookSecret());
    }

    @Test
    void filtersOutConfigsThatDoNotSubscribeToTheEvent() {
        UUID merchantId = UUID.randomUUID();
        String cipher = Base64.getEncoder().encodeToString("x".getBytes(StandardCharsets.UTF_8));

        when(webhookConfigRepository.findByMerchant_IdAndEnabledTrue(merchantId))
                .thenReturn(List.of(config(UUID.randomUUID(), "https://a.example", cipher, "refund.created")));

        assertTrue(service.getActiveConfigsForEvent(merchantId, "payment.captured").isEmpty());
    }

    @Test
    void returnsEmptyListWhenNoConfigsExist() {
        UUID merchantId = UUID.randomUUID();
        when(webhookConfigRepository.findByMerchant_IdAndEnabledTrue(merchantId)).thenReturn(List.of());

        assertTrue(service.getActiveConfigsForEvent(merchantId, "payment.captured").isEmpty());
    }

    @Test
    void listsOnlyActiveMerchantIds() {
        Merchant first = Merchant.builder().id(UUID.randomUUID()).status(MerchantStatus.ACTIVE).build();
        Merchant second = Merchant.builder().id(UUID.randomUUID()).status(MerchantStatus.ACTIVE).build();
        when(merchantRepository.findByStatus(MerchantStatus.ACTIVE)).thenReturn(List.of(first, second));

        assertEquals(List.of(first.getId(), second.getId()), service.listActiveMerchantIds());
    }

    @Test
    void mapsSettlementBankDetails() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = Merchant.builder()
                .id(merchantId)
                .settlementBankAccount("1234567890")
                .settlementBankIfsc("HDFC0000001")
                .settlementBankAccountHolderName("NeoPay Merchant")
                .build();
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));

        SettlementBankDetails details = service.getSettlementBankDetails(merchantId);

        assertEquals("1234567890", details.accountNumber());
        assertEquals("HDFC0000001", details.ifsc());
        assertEquals("NeoPay Merchant", details.accountHolderName());
    }

    @Test
    void unknownMerchantRaisesNotFound() {
        UUID merchantId = UUID.randomUUID();
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.empty());

        ResourceNotFoundException error = assertThrows(ResourceNotFoundException.class,
                () -> service.getSettlementBankDetails(merchantId));

        assertTrue(error.getMessage().contains(merchantId.toString()));
        verify(merchantRepository).findById(merchantId);
    }
}
