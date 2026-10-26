package com.snehal.neopay.payment.ledger;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LedgerInvariantCheckerTest {

    private LedgerLineRepository repository;
    private LedgerInvariantChecker checker;

    @BeforeEach
    void setUp() {
        repository = mock(LedgerLineRepository.class);
        checker = new LedgerInvariantChecker(repository, new SimpleMeterRegistry());
        checker.registerGauge();
    }

    @Test
    void balancedLedgerIsHealthy() {
        when(repository.totalDebits()).thenReturn(5_000L);
        when(repository.totalCredits()).thenReturn(5_000L);
        when(repository.findUnbalancedTransactions()).thenReturn(List.of());

        LedgerCheckResult result = checker.check();

        assertTrue(result.healthy());
        assertEquals(0, result.globalDelta());
    }

    @Test
    void globalDeltaIsReported() {
        when(repository.totalDebits()).thenReturn(5_000L);
        when(repository.totalCredits()).thenReturn(4_999L);
        when(repository.findUnbalancedTransactions()).thenReturn(List.of());

        LedgerCheckResult result = checker.check();

        assertFalse(result.healthy());
        assertEquals(1, result.globalDelta());
    }

    @Test
    void unbalancedTransactionIsReported() {
        UUID transactionId = UUID.randomUUID();
        when(repository.totalDebits()).thenReturn(100L);
        when(repository.totalCredits()).thenReturn(100L);
        when(repository.findUnbalancedTransactions()).thenReturn(List.of(transactionId));

        LedgerCheckResult result = checker.check();

        assertFalse(result.healthy());
        assertEquals(List.of(transactionId), result.unbalancedTransactions());
    }
}
