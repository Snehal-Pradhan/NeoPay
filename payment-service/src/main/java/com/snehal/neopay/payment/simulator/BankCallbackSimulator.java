package com.snehal.neopay.payment.simulator;

import com.snehal.neopay.common.domain.PaymentId;
import com.snehal.neopay.common.enums.ChaosMode;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.common.util.RandomizerUtil;
import com.snehal.neopay.payment.entity.Payment;
import com.snehal.neopay.payment.repository.PaymentRepository;
import com.snehal.neopay.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "payment.simulator", name = "enabled", havingValue = "true")
public class BankCallbackSimulator {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final SimulatorConfig simulatorConfig;

    @Scheduled(fixedDelayString = "${payment.simulator.poll-interval-ms:5000}")
    @SchedulerLock(name = "payment-service-bank-callback-simulator", lockAtMostFor = "10s", lockAtLeastFor = "1s")
    public void processCallbacks() {

        LocalDateTime globalWindow = LocalDateTime.now().minusSeconds(1);

        List<Payment> candidates = paymentRepository
                .findByStatusAndCreatedAtBefore(PaymentStatus.AUTHORIZING, globalWindow);

        log.info("Simulating payments for {} payments", candidates.size());

        if (candidates.isEmpty()) return;

        for (Payment payment: candidates) {
            simulateCallback(payment);
        }
    }

    private void simulateCallback(Payment payment) {
        SimulatorConfig.MethodSimulatorConfig methodConfig = simulatorConfig.configFor(payment.getMethod());

        LocalDateTime dueAt = dueAt(payment, methodConfig);

        if(LocalDateTime.now().isBefore(dueAt)) {
            return;
        }

        ChaosMode chaosMode = simulatorConfig.getChaosMode();

        switch (chaosMode) {
            case SUCCESS -> resolve(payment, true);
            case FAILURE -> resolve(payment, false);
            case TIMEOUT -> {
                log.debug("BankCallback simulator: Payment Timed out");
            }
            case NORMAL, SLOW -> resolve(payment, shouldApprove(payment, methodConfig));
        }
    }

    private void resolve(Payment payment, boolean approve) {
        if (approve) {
            String bankRef = "SIM_BANK_REF"+ RandomizerUtil.randomBase64(8);
            paymentService.resolveAuthorization(PaymentId.of(payment.getId()), true, bankRef, null, null);
        } else {
            paymentService.resolveAuthorization(PaymentId.of(payment.getId()), false, null, "SIM_BANK_ERROR_CODE", "Simulated Bank Decline");
        }
    }

    private boolean shouldApprove(Payment payment, SimulatorConfig.MethodSimulatorConfig methodConfig) {
        int bucket = Math.floorMod(payment.getId().hashCode(), 100);
        return bucket < methodConfig.getSuccessRate();
    }

    private LocalDateTime dueAt(Payment payment, SimulatorConfig.MethodSimulatorConfig methodConfig) {

        int range = methodConfig.getMaxDelaySeconds() - methodConfig.getMinDelaySeconds();
        int delaySeconds = methodConfig.getMinDelaySeconds()
                + Math.floorMod(payment.getId().hashCode(), range + 1);

        if (simulatorConfig.getChaosMode() == ChaosMode.SLOW) {
            delaySeconds *= 2;
        }

        return payment.getCreatedAt().plusSeconds(delaySeconds);
    }

}
