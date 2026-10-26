package com.snehal.neopay.payment.integration;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.OrderStatus;
import com.snehal.neopay.common.enums.PaymentMethod;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.payment.entity.OrderRecord;
import com.snehal.neopay.payment.entity.Payment;
import com.snehal.neopay.payment.repository.OrderRepository;
import com.snehal.neopay.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("integration")
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class PaymentIdempotencyConcurrencyTest {

    private static final int THREADS = 50;
    private static final String IDEMPOTENCY_KEY = "concurrent-key";

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
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void concurrentInsertsWithSameKeyProduceOnePayment() throws Exception {
        UUID merchantId = UUID.randomUUID();
        OrderRecord order = orderRepository.save(OrderRecord.builder()
                .merchantId(merchantId)
                .amount(Money.inr(1_000))
                .receipt("concurrency-" + UUID.randomUUID())
                .orderStatus(OrderStatus.CREATED)
                .attempts(0)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build());

        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicInteger inserted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    paymentRepository.saveAndFlush(Payment.builder()
                            .order(order)
                            .merchantId(merchantId)
                            .amount(Money.inr(1_000))
                            .idempotencyKey(IDEMPOTENCY_KEY)
                            .status(PaymentStatus.CREATED)
                            .method(PaymentMethod.UPI)
                            .build());
                    inserted.incrementAndGet();
                } catch (DataIntegrityViolationException conflict) {
                    rejected.incrementAndGet();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }));
        }

        ready.await(30, TimeUnit.SECONDS);
        start.countDown();
        done.await(60, TimeUnit.SECONDS);
        pool.shutdownNow();

        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }

        assertEquals(1, inserted.get(), "exactly one insert must succeed");
        assertEquals(THREADS - 1, rejected.get(), "all other inserts must be rejected");
        assertEquals(1, paymentRepository.count(), "exactly one payment row must exist");
    }
}
