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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LedgerServiceTest {

    private LedgerLineRepository repository;
    private LedgerService service;

    @BeforeEach
    void setUp() {
        repository = mock(LedgerLineRepository.class);
        service = new LedgerService(repository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void capturePostsBalancedPair() {
        UUID paymentId = UUID.randomUUID();

        service.postCapture(paymentId, Money.inr(1_000));

        ArgumentCaptor<List<LedgerLine>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        List<LedgerLine> lines = captor.getValue();

        assertEquals(2, lines.size());
        assertEquals(1_000, lines.stream().mapToLong(LedgerLine::getDebitUnits).sum());
        assertEquals(1_000, lines.stream().mapToLong(LedgerLine::getCreditUnits).sum());
        assertTrue(lines.stream().anyMatch(l -> l.getAccount() == LedgerAccount.CUSTOMER_FUNDS));
        assertTrue(lines.stream().anyMatch(l -> l.getAccount() == LedgerAccount.MERCHANT_PAYABLE));
    }

    @Test
    void nonPositiveAmountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.postPair(
                UUID.randomUUID(), LedgerAccount.CUSTOMER_FUNDS, LedgerAccount.MERCHANT_PAYABLE,
                Money.inr(0), LedgerReference.payment(UUID.randomUUID())));
    }

    @Test
    void sameAccountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.postPair(
                UUID.randomUUID(), LedgerAccount.CUSTOMER_FUNDS, LedgerAccount.CUSTOMER_FUNDS,
                Money.inr(100), LedgerReference.payment(UUID.randomUUID())));
    }

    @Test
    void replaySkipsAlreadyPostedPair() {
        UUID paymentId = UUID.randomUUID();
        when(repository.existsByTransactionIdAndAccount(paymentId, LedgerAccount.MERCHANT_PAYABLE))
                .thenReturn(true);

        service.postCapture(paymentId, Money.inr(500));

        verify(repository, never()).saveAll(any());
    }

    @Test
    void settlementRejectsComponentsThatDoNotSumToGross() {
        assertThrows(IllegalArgumentException.class, () -> new SettlementLedgerRequest(
                UUID.randomUUID(), 1_000, 20, 4, 0, 0, 900, "INR"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void settlementPostsOnePairPerPositiveComponent() {
        UUID settlementId = UUID.randomUUID();
        SettlementLedgerRequest request = new SettlementLedgerRequest(
                settlementId, 1_000, 20, 4, 0, 0, 976, "INR");

        service.postSettlement(request);

        ArgumentCaptor<List<LedgerLine>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository, org.mockito.Mockito.times(3)).saveAll(captor.capture());
        assertEquals(3, captor.getAllValues().size());
    }

    @Test
    void zeroComponentIsNotPosted() {
        UUID settlementId = UUID.randomUUID();
        SettlementLedgerRequest request = new SettlementLedgerRequest(
                settlementId, 1_000, 0, 0, 0, 0, 1_000, "INR");

        service.postSettlement(request);

        verify(repository, org.mockito.Mockito.times(1)).saveAll(any());
    }

    @Test
    void losingAConcurrentInsertRaceIsTreatedAsReplay() {
        UUID paymentId = UUID.randomUUID();
        when(repository.existsByTransactionIdAndAccount(any(), any())).thenReturn(false);
        when(repository.saveAll(any())).thenThrow(
                new org.springframework.dao.DataIntegrityViolationException(
                        "duplicate key value violates unique constraint ux_ledger_line_txn_account"));

        UUID result = service.postCapture(paymentId, Money.inr(1_000));

        assertEquals(paymentId, result);
    }
}
