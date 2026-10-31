package com.snehal.neopay.payment.dispute;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Closes disputes whose evidence deadline passed unanswered. The sweep is idempotent:
 * a dispute leaves {@code EVIDENCE_REQUIRED} on the first close, so later sweeps skip it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DisputeDeadlineScheduler {

    private final DisputeService disputeService;

    @Scheduled(fixedDelayString = "${dispute.evidence-sweep-ms:60000}")
    @SchedulerLock(name = "payment-service-dispute-evidence-sweep",
            lockAtMostFor = "5m", lockAtLeastFor = "10s")
    public void sweep() {
        int closed = disputeService.closeExpiredEvidenceWindows(LocalDateTime.now());
        if (closed > 0) {
            log.info("Closed {} disputes for missed evidence deadlines", closed);
        }
    }
}
