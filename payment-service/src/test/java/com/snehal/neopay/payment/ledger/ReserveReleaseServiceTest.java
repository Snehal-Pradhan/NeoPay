package com.snehal.neopay.payment.ledger;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReserveReleaseServiceTest {

    private LedgerLineRepository ledgerLineRepository;
    private ReserveReleaseRepository releaseRepository;
    private LedgerService ledgerService;
    private ReserveReleaseService service;

    @BeforeEach
    void setUp() {
        ledgerLineRepository = mock(LedgerLineRepository.class);
        releaseRepository = mock(ReserveReleaseRepository.class);
        ledgerService = mock(LedgerService.class);
        service = new ReserveReleaseService(ledgerLineRepository, releaseRepository,
                ledgerService, new SimpleMeterRegistry());
        ReflectionTestUtils.setField(service, "holdDays", 7L);
    }

    @Test
    void releasesEachDueReserveLineOnce() {
        UUID settlementId = UUID.randomUUID();
        LedgerLine line = LedgerLine.builder()
                .id(UUID.randomUUID())
                .transactionId(settlementId)
                .account(LedgerAccount.MERCHANT_RESERVE)
                .debitUnits(0)
                .creditUnits(500)
                .currency("INR")
                .referenceType(LedgerReference.SETTLEMENT)
                .referenceId(settlementId)
                .postedAt(LocalDateTime.now().minusDays(10))
                .build();
        when(ledgerLineRepository.findReleasableReserveLines(eq(LedgerAccount.MERCHANT_RESERVE), any()))
                .thenReturn(List.of(line));

        int released = service.releaseDue();

        assertEquals(1, released);
        verify(ledgerService).postPair(any(), eq(LedgerAccount.MERCHANT_RESERVE),
                eq(LedgerAccount.BANK_SETTLEMENT), any(), any());
        verify(releaseRepository).save(any(ReserveRelease.class));
    }

    @Test
    void nothingDueIsNoOp() {
        when(ledgerLineRepository.findReleasableReserveLines(any(), any())).thenReturn(List.of());

        assertEquals(0, service.releaseDue());
        verify(ledgerService, never()).postPair(any(), any(), any(), any(), any());
    }

    @Test
    void scheduledReleaseDelegatesToTheTransactionalRelease() {
        when(ledgerLineRepository.findReleasableReserveLines(eq(LedgerAccount.MERCHANT_RESERVE), any()))
                .thenReturn(List.of());

        service.scheduledRelease();

        verify(ledgerLineRepository).findReleasableReserveLines(eq(LedgerAccount.MERCHANT_RESERVE), any());
    }
}
