package com.snehal.neopay.payment.ledger;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Verifies that every ledger transaction balances and that global debits equal global
 * credits. Runs on a schedule and exposes a gauge for alerting.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LedgerInvariantChecker {

    private final LedgerLineRepository repository;
    private final MeterRegistry registry;
    private final AtomicLong unbalancedCount = new AtomicLong();

    @PostConstruct
    void registerGauge() {
        Gauge.builder("ledger_unbalanced_transactions", unbalancedCount, AtomicLong::doubleValue)
                .description("Transactions whose debits do not equal credits")
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${ledger.check-interval-ms:60000}")
    @SchedulerLock(name = "payment-service-ledger-invariant-checker",
            lockAtMostFor = "1m", lockAtLeastFor = "1s")
    public void scheduledCheck() {
        check();
    }

    public LedgerCheckResult check() {
        long delta = repository.totalDebits() - repository.totalCredits();
        List<UUID> unbalanced = repository.findUnbalancedTransactions();
        unbalancedCount.set(unbalanced.size());

        if (delta != 0 || !unbalanced.isEmpty()) {
            log.error("Ledger imbalance detected: delta={} unbalancedTransactions={}",
                    delta, unbalanced.size());
        }
        return new LedgerCheckResult(delta, List.copyOf(unbalanced));
    }
}
