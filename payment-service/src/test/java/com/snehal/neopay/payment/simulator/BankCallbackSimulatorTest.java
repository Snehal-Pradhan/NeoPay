package com.snehal.neopay.payment.simulator;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.ChaosMode;
import com.snehal.neopay.common.enums.PaymentMethod;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.payment.entity.Payment;
import com.snehal.neopay.payment.repository.PaymentRepository;
import com.snehal.neopay.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import com.snehal.neopay.common.domain.PaymentId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BankCallbackSimulatorTest {

    private PaymentRepository repository;
    private PaymentService paymentService;
    private SimulatorConfig config;
    private BankCallbackSimulator simulator;

    @BeforeEach
    void setUp() {
        repository = mock(PaymentRepository.class);
        paymentService = mock(PaymentService.class);
        config = new SimulatorConfig();
        simulator = new BankCallbackSimulator(repository, paymentService, config);

        SimulatorConfig.MethodSimulatorConfig method = new SimulatorConfig.MethodSimulatorConfig();
        method.setMinDelaySeconds(0);
        method.setMaxDelaySeconds(0);
        config.getMethods().put("UPI", method);
    }

    @Test
    void noCandidatesDoesNothing() {
        when(repository.findByStatusAndCreatedAtBefore(eq(PaymentStatus.AUTHORIZING), any()))
                .thenReturn(List.of());

        simulator.processCallbacks();

        verify(paymentService, never()).resolveAuthorization(any(), anyBoolean(), any(), any(), any());
    }

    @Test
    void successModeApproves() {
        Payment payment = payment();
        when(repository.findByStatusAndCreatedAtBefore(eq(PaymentStatus.AUTHORIZING), any()))
                .thenReturn(List.of(payment));
        config.setChaosMode(ChaosMode.SUCCESS);

        simulator.processCallbacks();

        verify(paymentService).resolveAuthorization(eq(PaymentId.of(payment.getId())), eq(true),
                anyString(), isNull(), isNull());
    }

    @Test
    void failureModeDeclines() {
        Payment payment = payment();
        when(repository.findByStatusAndCreatedAtBefore(eq(PaymentStatus.AUTHORIZING), any()))
                .thenReturn(List.of(payment));
        config.setChaosMode(ChaosMode.FAILURE);

        simulator.processCallbacks();

        verify(paymentService).resolveAuthorization(eq(PaymentId.of(payment.getId())), eq(false),
                isNull(), anyString(), anyString());
    }

    @Test
    void timeoutModeDoesNotResolve() {
        Payment payment = payment();
        when(repository.findByStatusAndCreatedAtBefore(eq(PaymentStatus.AUTHORIZING), any()))
                .thenReturn(List.of(payment));
        config.setChaosMode(ChaosMode.TIMEOUT);

        simulator.processCallbacks();

        verify(paymentService, never()).resolveAuthorization(any(), anyBoolean(), any(), any(), any());
    }

    @Test
    void futureDueTimeIsSkipped() {
        Payment payment = payment();
        SimulatorConfig.MethodSimulatorConfig method = config.getMethods().get("UPI");
        method.setMinDelaySeconds(600);
        method.setMaxDelaySeconds(600);
        when(repository.findByStatusAndCreatedAtBefore(eq(PaymentStatus.AUTHORIZING), any()))
                .thenReturn(List.of(payment));
        config.setChaosMode(ChaosMode.SUCCESS);

        simulator.processCallbacks();

        verify(paymentService, never()).resolveAuthorization(any(), anyBoolean(), any(), any(), any());
    }

    @Test
    void normalModeApprovesWhenSuccessRateIsFull() {
        Payment payment = payment();
        config.getMethods().get("UPI").setSuccessRate(100);
        when(repository.findByStatusAndCreatedAtBefore(eq(PaymentStatus.AUTHORIZING), any()))
                .thenReturn(List.of(payment));
        config.setChaosMode(ChaosMode.NORMAL);

        simulator.processCallbacks();

        verify(paymentService).resolveAuthorization(eq(PaymentId.of(payment.getId())), eq(true),
                anyString(), isNull(), isNull());
    }

    private static Payment payment() {
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .amount(Money.inr(500))
                .status(PaymentStatus.AUTHORIZING)
                .method(PaymentMethod.UPI)
                .build();
        payment.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        return payment;
    }
}
