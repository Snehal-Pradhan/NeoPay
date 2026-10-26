package com.snehal.neopay.payment.ledger;

import com.snehal.neopay.common.dto.SettlementLedgerRequest;
import com.snehal.neopay.common.entity.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Refund, payout-return and settlement posting rules not covered by LedgerServiceTest. */
class LedgerServiceAdditionalTest {

    private LedgerLineRepository repository;
    private LedgerService service;

    @BeforeEach
    void setUp() {
        repository = mock(LedgerLineRepository.class);
        service = new LedgerService(repository);
    }

    @Test
    void postPairReturnsTheTransactionId() {
        UUID transactionId = UUID.randomUUID();

        UUID result = service.postPair(transactionId, LedgerAccount.CUSTOMER_FUNDS,
                LedgerAccount.MERCHANT_PAYABLE, Money.inr(100),
                LedgerReference.payment(transactionId));

        assertEquals(transactionId, result);
        verify(repository).saveAll(any());
    }

    @Test
    void nullTransactionIdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.postPair(null,
                LedgerAccount.CUSTOMER_FUNDS, LedgerAccount.MERCHANT_PAYABLE,
                Money.inr(100), LedgerReference.payment(UUID.randomUUID())));
    }

    @Test
    void nullAccountIsRejected() {
        UUID transactionId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> service.postPair(transactionId,
                null, LedgerAccount.MERCHANT_PAYABLE, Money.inr(100),
                LedgerReference.payment(transactionId)));
    }

    @Test
    void unsettledRefundDrawsFromMerchantPayable() {
        UUID refundId = UUID.randomUUID();

        service.postRefund(refundId, Money.inr(250), false);

        List<LedgerLine> lines = capturedLines(1);
        assertTrue(lines.stream().anyMatch(l -> l.getAccount() == LedgerAccount.MERCHANT_PAYABLE
                && l.getDebitUnits() == 250));
        assertTrue(lines.stream().anyMatch(l -> l.getAccount() == LedgerAccount.CUSTOMER_FUNDS
                && l.getCreditUnits() == 250));
    }

    @Test
    void settledRefundDrawsFromBankSettlement() {
        service.postRefund(UUID.randomUUID(), Money.inr(250), true);

        List<LedgerLine> lines = capturedLines(1);
        assertTrue(lines.stream().anyMatch(l -> l.getAccount() == LedgerAccount.BANK_SETTLEMENT));
    }

    @Test
    void payoutReturnRestoresMerchantPayable() {
        UUID payoutId = UUID.randomUUID();

        UUID result = service.postPayoutReturn(payoutId, Money.inr(400));

        assertEquals(payoutId, result);
        List<LedgerLine> lines = capturedLines(1);
        assertTrue(lines.stream().anyMatch(l -> l.getAccount() == LedgerAccount.BANK_SETTLEMENT
                && l.getDebitUnits() == 400));
        assertTrue(lines.stream().anyMatch(l -> l.getAccount() == LedgerAccount.MERCHANT_PAYABLE
                && l.getCreditUnits() == 400));
        assertTrue(lines.stream().allMatch(l -> "PAYOUT_RETURN".equals(l.getReferenceType())));
    }

    @Test
    void settlementPostsEveryPositiveComponent() {
        UUID settlementId = UUID.randomUUID();
        SettlementLedgerRequest request = new SettlementLedgerRequest(
                settlementId, 1_000, 20, 4, 1, 5, 970, "INR");

        service.postSettlement(request);

        verify(repository, times(5)).saveAll(any());
    }

    @Test
    void settlementWithoutComponentsDoesNotPost() {
        SettlementLedgerRequest request = new SettlementLedgerRequest(
                UUID.randomUUID(), 0, 0, 0, 0, 0, 0, "INR");

        service.postSettlement(request);

        verify(repository, never()).saveAll(any());
    }

    @Test
    void replayDoesNotWriteAgain() {
        UUID transactionId = UUID.randomUUID();
        when(repository.existsByTransactionIdAndAccount(transactionId,
                LedgerAccount.MERCHANT_PAYABLE)).thenReturn(true);

        assertEquals(transactionId, service.postCapture(transactionId, Money.inr(100)));
        verify(repository, never()).saveAll(any());
    }

    @SuppressWarnings("unchecked")
    private List<LedgerLine> capturedLines(int times) {
        ArgumentCaptor<List<LedgerLine>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository, times(times)).saveAll(captor.capture());
        return captor.getAllValues().stream().flatMap(List::stream).toList();
    }
}
