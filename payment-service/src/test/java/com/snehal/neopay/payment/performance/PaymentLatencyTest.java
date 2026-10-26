package com.snehal.neopay.payment.performance;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.OrderStatus;
import com.snehal.neopay.common.enums.PaymentMethod;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.payment.dto.request.PaymentInitRequest;
import com.snehal.neopay.payment.dto.response.PaymentResponse;
import com.snehal.neopay.payment.entity.OrderRecord;
import com.snehal.neopay.payment.gateway.PaymentGatewayRouter;
import com.snehal.neopay.payment.gateway.dto.PaymentResult;
import com.snehal.neopay.payment.repository.OrderRepository;
import com.snehal.neopay.payment.service.PaymentService;
import com.snehal.neopay.common.domain.MerchantId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Service-level latency baseline for payment initiation.
 *
 * Targets: p99 under 300 ms for POST /v1/payments, zero errors.
 * The measurement covers order insert, payment insert, state transition,
 * outbox write and the (mocked) gateway call.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class PaymentLatencyTest {

    private static final int WARMUP = 50;
    private static final int SAMPLES = 300;
    private static final long P99_BUDGET_NANOS = 300_000_000L;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "?sslmode=disable");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @MockitoBean
    private PaymentGatewayRouter gatewayRouter;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void paymentInitiationP99WithinBudget() {
        when(gatewayRouter.initiate(any()))
                .thenReturn(new PaymentResult.Pending("UPI_PROCESSOR_perf"));

        for (int i = 0; i < WARMUP; i++) {
            assertEquals(PaymentStatus.AUTHORIZING, initiateOnce());
        }

        long[] latencies = new long[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            long start = System.nanoTime();
            PaymentStatus status = initiateOnce();
            latencies[i] = System.nanoTime() - start;
            assertEquals(PaymentStatus.AUTHORIZING, status);
        }

        Arrays.sort(latencies);
        long p50 = percentile(latencies, 50);
        long p95 = percentile(latencies, 95);
        long p99 = percentile(latencies, 99);

        System.out.printf("payment.initiate latency: p50=%dms p95=%dms p99=%dms samples=%d%n",
                p50 / 1_000_000, p95 / 1_000_000, p99 / 1_000_000, SAMPLES);

        assertTrue(p99 < P99_BUDGET_NANOS,
                "p99 " + (p99 / 1_000_000) + "ms exceeds budget "
                        + (P99_BUDGET_NANOS / 1_000_000) + "ms");
    }

    private PaymentStatus initiateOnce() {
        UUID merchantId = UUID.randomUUID();
        OrderRecord order = orderRepository.save(OrderRecord.builder()
                .merchantId(merchantId)
                .amount(Money.inr(1_000))
                .receipt("perf-" + UUID.randomUUID())
                .orderStatus(OrderStatus.CREATED)
                .attempts(0)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build());

        PaymentResponse response = paymentService.initiate(MerchantId.of(merchantId),
                new PaymentInitRequest(order.getId(), PaymentMethod.UPI,
                        Map.of("vpa", "success@okaxis")),
                UUID.randomUUID().toString());

        return response.status();
    }

    private static long percentile(long[] sorted, int percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }
}
