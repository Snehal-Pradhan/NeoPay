package com.snehal.neopay.payment.ledger;

import com.snehal.neopay.common.entity.Money;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Releases retained merchant reserve to the settlement account after the hold period.
 * Each reserve line is released at most once, enforced by the unique source line id.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReserveReleaseService {

    private final LedgerLineRepository ledgerLineRepository;
    private final ReserveReleaseRepository releaseRepository;
    private final LedgerService ledgerService;
    private final MeterRegistry registry;

    @Value("${reserve.hold-days:7}")
    private long holdDays;

    @Transactional
    @Scheduled(fixedDelayString = "${reserve.release-interval-ms:3600000}")
    @SchedulerLock(name = "payment-service-reserve-release",
            lockAtMostFor = "10m", lockAtLeastFor = "1m")
    public void scheduledRelease() {
        releaseDue();
    }

    @Transactional
    public int releaseDue() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(holdDays);
        List<LedgerLine> releasable = ledgerLineRepository
                .findReleasableReserveLines(LedgerAccount.MERCHANT_RESERVE, cutoff);

        int released = 0;
        for (LedgerLine line : releasable) {
            UUID transactionId = UUID.randomUUID();
            ledgerService.postPair(transactionId, LedgerAccount.MERCHANT_RESERVE,
                    LedgerAccount.BANK_SETTLEMENT,
                    Money.of(line.getCreditUnits(), line.getCurrency()),
                    LedgerReference.reserveRelease(line.getReferenceId()));

            releaseRepository.save(ReserveRelease.builder()
                    .sourceLineId(line.getId())
                    .transactionId(transactionId)
                    .releasedAt(LocalDateTime.now())
                    .build());

            counter().increment();
            released++;
        }

        if (released > 0) {
            log.info("Released {} merchant reserve lines", released);
        }
        return released;
    }

    private Counter counter() {
        return Counter.builder("reserve_released_total").register(registry);
    }
}
