package com.snehal.neopay.vault.service.impl;

import com.snehal.neopay.common.dto.PaymentProcessorResponse;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.CardBrand;
import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.vault.dto.request.TokenizeRequest;
import com.snehal.neopay.vault.dto.response.TokenizeResponse;
import com.snehal.neopay.vault.entity.CardToken;
import com.snehal.neopay.vault.entity.VaultCard;
import com.snehal.neopay.vault.processor.CardPaymentProcessor;
import com.snehal.neopay.vault.repository.CardTokenRepository;
import com.snehal.neopay.vault.repository.VaultCardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.security.crypto.encrypt.BytesEncryptor;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VaultServiceImplTest {

    private CardTokenRepository cardTokenRepository;
    private VaultCardRepository vaultCardRepository;
    private BytesEncryptor dekEncrypter;
    private CardPaymentProcessor cardPaymentProcessor;
    private VaultServiceImpl service;

    @BeforeEach
    void setUp() {
        cardTokenRepository = mock(CardTokenRepository.class);
        vaultCardRepository = mock(VaultCardRepository.class);
        dekEncrypter = mock(BytesEncryptor.class);
        cardPaymentProcessor = mock(CardPaymentProcessor.class);
        service = new VaultServiceImpl(cardTokenRepository, vaultCardRepository,
                dekEncrypter, cardPaymentProcessor);
    }

    @Test
    void tokenizeStoresOnlyMaskedCardData() {
        when(dekEncrypter.encrypt(any())).thenReturn(new byte[]{1, 2, 3});
        when(vaultCardRepository.save(any(VaultCard.class))).thenAnswer(i -> i.getArgument(0));
        when(cardTokenRepository.save(any(CardToken.class))).thenAnswer(i -> i.getArgument(0));

        TokenizeResponse response = service.tokenize(visa(), UUID.randomUUID());

        ArgumentCaptor<VaultCard> cardCaptor = ArgumentCaptor.forClass(VaultCard.class);
        verify(vaultCardRepository).save(cardCaptor.capture());
        VaultCard stored = cardCaptor.getValue();

        assertEquals("1111", stored.getLastFour());
        assertEquals("411111", stored.getBin());
        assertEquals(CardBrand.VISA, stored.getBrand());
        assertEquals("Test User", stored.getCardHolderName());
        assertTrue(stored.getEncryptedPan().length > 0);
    }

    @Test
    void tokenizeReturnsGeneratedTokenWithPrefix() {
        when(dekEncrypter.encrypt(any())).thenReturn(new byte[]{1});
        when(vaultCardRepository.save(any(VaultCard.class))).thenAnswer(i -> i.getArgument(0));
        when(cardTokenRepository.save(any(CardToken.class))).thenAnswer(i -> i.getArgument(0));

        TokenizeResponse response = service.tokenize(visa(), UUID.randomUUID());

        assertTrue(response.token().startsWith("tok_"));
        assertEquals("1111", response.lastFour());
        assertEquals(CardBrand.VISA, response.brand());
    }

    @Test
    void brandDetection() {
        assertEquals(CardBrand.VISA, brand("4111111111111111"));
        assertEquals(CardBrand.MASTERCARD, brand("5555555555554444"));
        assertEquals(CardBrand.AMEX, brand("378282246310005"));
        assertEquals(CardBrand.RUPAY, brand("6521555555555555"));
    }

    @Test
    void chargeOfUnknownTokenThrows() {
        when(cardTokenRepository.findByTokenAndRevokedAtIsNull("tok_missing"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.charge(UUID.randomUUID(), "tok_missing", null, null));
    }

    private CardBrand brand(String pan) {
        when(dekEncrypter.encrypt(any())).thenReturn(new byte[]{1});
        when(vaultCardRepository.save(any(VaultCard.class))).thenAnswer(i -> i.getArgument(0));
        when(cardTokenRepository.save(any(CardToken.class))).thenAnswer(i -> i.getArgument(0));
        TokenizeRequest request = new TokenizeRequest(pan, "123", 12, 2030, null, "Test User");
        return service.tokenize(request, UUID.randomUUID()).brand();
    }

    private static TokenizeRequest visa() {
        return new TokenizeRequest("4111111111111111", "123", 12, 2030, null, "Test User");
    }

    @Test
    void chargeSuccessReturnsProcessorResponse() {
        byte[] dek = new byte[32];
        byte[] encryptedPan = com.snehal.neopay.vault.config.VaultEncryptionConfig
                .panEncrypter(dek).encrypt("4111111111111111".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        VaultCard card = VaultCard.builder()
                .encryptedDek(new byte[]{9})
                .encryptedPan(encryptedPan)
                .expiryMonth("12")
                .expiryYear("2030")
                .build();
        CardToken token = CardToken.builder().token("tok_1").vaultCard(card).build();

        when(dekEncrypter.decrypt(any())).thenReturn(dek);
        when(cardTokenRepository.findByTokenAndRevokedAtIsNull("tok_1"))
                .thenReturn(Optional.of(token));
        when(cardPaymentProcessor.charge(any())).thenReturn(CompletableFuture.completedFuture(
                new PaymentProcessorResponse.Success("proc", "bank")));

        var response = service.charge(UUID.randomUUID(), "tok_1", Money.inr(100), Map.of());

        org.junit.jupiter.api.Assertions.assertInstanceOf(
                PaymentProcessorResponse.Success.class, response);
    }

    @Test
    void chargeFailureIsWrapped() {
        byte[] dek = new byte[32];
        byte[] encryptedPan = com.snehal.neopay.vault.config.VaultEncryptionConfig
                .panEncrypter(dek).encrypt("4111111111111111".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        VaultCard card = VaultCard.builder()
                .encryptedDek(new byte[]{9})
                .encryptedPan(encryptedPan)
                .expiryMonth("12")
                .expiryYear("2030")
                .build();
        CardToken token = CardToken.builder().token("tok_2").vaultCard(card).build();

        when(dekEncrypter.decrypt(any())).thenReturn(dek);
        when(cardTokenRepository.findByTokenAndRevokedAtIsNull("tok_2"))
                .thenReturn(Optional.of(token));
        when(cardPaymentProcessor.charge(any())).thenThrow(new IllegalStateException("processor down"));

        var response = service.charge(UUID.randomUUID(), "tok_2", Money.inr(100), Map.of());

        org.junit.jupiter.api.Assertions.assertEquals("VAULT_CHARGE_FAILED",
                ((PaymentProcessorResponse.Failure) response).errorCode());
    }

    @Test
    void mastercardTwoSeriesAndAmexThirtyFourSeriesAreDetected() {
        assertEquals(CardBrand.MASTERCARD, brand("2221000000000009"));
        assertEquals(CardBrand.AMEX, brand("341111111111111"));
    }

    @Test
    void decryptionFailureBeforePanIsReadStillReturnsFailure() {
        VaultCard card = VaultCard.builder()
                .encryptedDek(new byte[]{9})
                .encryptedPan(new byte[]{1})
                .expiryMonth("12")
                .expiryYear("2030")
                .build();
        CardToken token = CardToken.builder().token("tok_3").vaultCard(card).build();

        when(cardTokenRepository.findByTokenAndRevokedAtIsNull("tok_3")).thenReturn(Optional.of(token));
        when(dekEncrypter.decrypt(any())).thenThrow(new IllegalStateException("bad dek"));

        var response = service.charge(UUID.randomUUID(), "tok_3", Money.inr(100), Map.of());

        assertEquals("VAULT_CHARGE_FAILED", ((PaymentProcessorResponse.Failure) response).errorCode());
    }

    @Test
    void panBytesAreZeroedAfterCharge() {
        byte[] panBytes = "4111111111111111".getBytes(StandardCharsets.UTF_8);
        VaultCard card = VaultCard.builder()
                .encryptedDek(new byte[]{9})
                .encryptedPan(new byte[]{1})
                .expiryMonth("12")
                .expiryYear("2030")
                .build();
        CardToken token = CardToken.builder().token("tok_wipe").vaultCard(card).build();

        when(cardTokenRepository.findByTokenAndRevokedAtIsNull("tok_wipe")).thenReturn(Optional.of(token));
        when(dekEncrypter.decrypt(any())).thenReturn(new byte[32]);
        when(cardPaymentProcessor.charge(any())).thenReturn(
                CompletableFuture.completedFuture(new PaymentProcessorResponse.Pending("proc")));

        BytesEncryptor panEncrypter = mock(BytesEncryptor.class);
        when(panEncrypter.decrypt(any())).thenReturn(panBytes);

        try (MockedStatic<com.snehal.neopay.vault.config.VaultEncryptionConfig> mocked =
                     org.mockito.Mockito.mockStatic(com.snehal.neopay.vault.config.VaultEncryptionConfig.class)) {
            mocked.when(() -> com.snehal.neopay.vault.config.VaultEncryptionConfig.panEncrypter(any()))
                    .thenReturn(panEncrypter);

            service.charge(UUID.randomUUID(), "tok_wipe", Money.inr(100), Map.of());
        }

        assertArrayEquals(new byte[panBytes.length], panBytes);
    }
}
