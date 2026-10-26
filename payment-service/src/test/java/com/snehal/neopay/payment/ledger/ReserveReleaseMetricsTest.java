package com.snehal.neopay.payment.ledger;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Verifies the release counter and the release log message. */
class ReserveReleaseMetricsTest {

    private LedgerLineRepository ledgerLineRepository;
    private ReserveReleaseRepository releaseRepository;
    private SimpleMeterRegistry registry;
    private ReserveReleaseService service;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        ledgerLineRepository = mock(LedgerLineRepository.class);
        releaseRepository = mock(ReserveReleaseRepository.class);
        registry = new SimpleMeterRegistry();
        service = new ReserveReleaseService(ledgerLineRepository, releaseRepository,
                mock(LedgerService.class), registry);
        ReflectionTestUtils.setField(service, "holdDays", 7L);

        appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(ReserveReleaseService.class)).addAppender(appender);
    }

    @Test
    void releasedLineIncrementsCounterAndLogs() {
        when(ledgerLineRepository.findReleasableReserveLines(
                eq(LedgerAccount.MERCHANT_RESERVE), any())).thenReturn(List.of(line()));

        assertEquals(1, service.releaseDue());
        assertEquals(1.0, registry.get("reserve_released_total").counter().count());
        assertTrue(appender.list.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("Released 1 merchant reserve")));
    }

    @Test
    void noReleaseDoesNotLogOrCount() {
        when(ledgerLineRepository.findReleasableReserveLines(any(), any())).thenReturn(List.of());

        assertEquals(0, service.releaseDue());
        assertNull(registry.find("reserve_released_total").counter());
        assertTrue(appender.list.isEmpty());
    }

    private static LedgerLine line() {
        UUID settlementId = UUID.randomUUID();
        return LedgerLine.builder()
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
    }
}
