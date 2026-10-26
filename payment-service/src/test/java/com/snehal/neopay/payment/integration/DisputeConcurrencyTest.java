package com.snehal.neopay.payment.integration;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.exception.BusinessRuleViolationException;
import com.snehal.neopay.payment.dispute.Dispute;
import com.snehal.neopay.payment.dispute.DisputeRepository;
import com.snehal.neopay.payment.dispute.DisputeService;
import com.snehal.neopay.payment.dispute.DisputeStatus;
import com.snehal.neopay.payment.ledger.LedgerAccount;
import com.snehal.neopay.payment.ledger.LedgerLineRepository;
import org.junit.jupiter.api.Tag;
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

@Tag("integration")
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class DisputeConcurrencyTest {

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
    private DisputeService disputeService;

    @Autowired
    private DisputeRepository disputeRepository;

    @Autowired
    private LedgerLineRepository ledgerLineRepository;

    @Test
    void concurrentResolutionsPostOnceAndRejectTheLoser() throws Exception {
        Dispute dispute = disputeRepository.save(Dispute.builder()
                .paymentId(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .amount(Money.inr(1_000))
                .status(DisputeStatus.OPENED)
                .sourceAccount(LedgerAccount.MERCHANT_PAYABLE)
                .reason("chargeback")
                .openedAt(LocalDateTime.now())
                .build());

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger resolved = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    disputeService.resolve(dispute.getId(), DisputeStatus.WON);
                    resolved.incrementAndGet();
                } catch (BusinessRuleViolationException alreadyResolved) {
                    rejected.incrementAndGet();
                } catch (Exception other) {
                    rejected.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertEquals(1, resolved.get(), "exactly one resolution must win");
        assertEquals(1, rejected.get(), "the second resolution must be rejected");
        assertEquals(2, ledgerLineRepository
                        .findByReferenceTypeAndReferenceId("DISPUTE", dispute.getId()).size(),
                "one dispute must post exactly one balanced pair");
    }
}
