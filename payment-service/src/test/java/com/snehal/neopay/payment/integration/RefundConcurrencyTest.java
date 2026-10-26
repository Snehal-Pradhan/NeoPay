package com.snehal.neopay.payment.integration;

import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.common.domain.PaymentId;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.OrderStatus;
import com.snehal.neopay.common.enums.PaymentMethod;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.common.exception.IdempotencyConflictException;
import com.snehal.neopay.payment.dto.request.CreateRefundRequest;
import com.snehal.neopay.payment.entity.OrderRecord;
import com.snehal.neopay.payment.entity.Payment;
import com.snehal.neopay.payment.ledger.LedgerService;
import com.snehal.neopay.payment.repository.OrderRepository;
import com.snehal.neopay.payment.repository.PaymentRepository;
import com.snehal.neopay.payment.service.RefundService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class RefundConcurrencyTest {

    private static final int THREADS = 50;

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

    @Autowired
    private RefundService refundService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private LedgerService ledgerService;

    @Test
    void concurrentRefundsWithSameKeyCreateOneRow() throws Exception {
        Payment payment = capturedPayment();
        ledgerService.postCapture(payment.getId(), payment.getAmount());

        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    refundService.create(MerchantId.of(payment.getMerchantId()),
                            PaymentId.of(payment.getId()),
                            new CreateRefundRequest(Money.inr(100), "concurrent"), "same-key");
                    accepted.incrementAndGet();
                } catch (IdempotencyConflictException conflict) {
                    conflicts.incrementAndGet();
                } catch (Exception other) {
                    // A duplicate-key race may surface as a wrapped data exception.
                    conflicts.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(30, TimeUnit.SECONDS);
        start.countDown();
        done.await(60, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertEquals(THREADS, accepted.get() + conflicts.get());
        assertEquals(1, refundService.list(MerchantId.of(payment.getMerchantId()),
                PaymentId.of(payment.getId())).size(),
                "exactly one refund row must exist");
        assertTrue(accepted.get() >= 1, "at least one request must be accepted");
    }

    private Payment capturedPayment() {
        UUID merchantId = UUID.randomUUID();
        OrderRecord order = orderRepository.save(OrderRecord.builder()
                .merchantId(merchantId)
                .amount(Money.inr(1_000))
                .receipt("conc-" + UUID.randomUUID())
                .orderStatus(OrderStatus.PAID)
                .attempts(1)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build());

        return paymentRepository.save(Payment.builder()
                .order(order)
                .merchantId(merchantId)
                .amount(Money.inr(1_000))
                .idempotencyKey("pay-" + UUID.randomUUID())
                .status(PaymentStatus.CAPTURED)
                .method(PaymentMethod.UPI)
                .build());
    }
}
