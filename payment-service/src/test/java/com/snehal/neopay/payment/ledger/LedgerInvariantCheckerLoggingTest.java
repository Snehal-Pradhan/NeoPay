package com.snehal.neopay.payment.ledger;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Asserts the observable log and gauge side effects of the invariant check. */
class LedgerInvariantCheckerLoggingTest {

    private LedgerLineRepository repository;
    private SimpleMeterRegistry registry;
    private LedgerInvariantChecker checker;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        repository = mock(LedgerLineRepository.class);
        registry = new SimpleMeterRegistry();
        checker = new LedgerInvariantChecker(repository, registry);
        checker.registerGauge();

        appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(LedgerInvariantChecker.class)).addAppender(appender);
    }

    @Test
    void unbalancedRowsLogEvenWhenTotalsMatch() {
        when(repository.totalDebits()).thenReturn(100L);
        when(repository.totalCredits()).thenReturn(100L);
        when(repository.findUnbalancedTransactions())
                .thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));

        checker.check();

        assertTrue(appender.list.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("Ledger imbalance")));
        assertEquals(2.0, registry.get("ledger_unbalanced_transactions").gauge().value());
    }

    @Test
    void globalDeltaLogsEvenWithoutUnbalancedRows() {
        when(repository.totalDebits()).thenReturn(100L);
        when(repository.totalCredits()).thenReturn(90L);
        when(repository.findUnbalancedTransactions()).thenReturn(List.of());

        checker.check();

        assertTrue(appender.list.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("Ledger imbalance")));
        assertEquals(0.0, registry.get("ledger_unbalanced_transactions").gauge().value());
    }

    @Test
    void healthyLedgerDoesNotLog() {
        when(repository.totalDebits()).thenReturn(50L);
        when(repository.totalCredits()).thenReturn(50L);
        when(repository.findUnbalancedTransactions()).thenReturn(List.of());

        checker.check();

        assertTrue(appender.list.isEmpty());
    }
}
