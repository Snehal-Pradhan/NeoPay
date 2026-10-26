package com.snehal.neopay.payment.integration;

import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.common.domain.PaymentId;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.OrderStatus;
import com.snehal.neopay.common.enums.PaymentMethod;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.common.exception.BusinessRuleViolationException;
import com.snehal.neopay.payment.dto.request.CreateRefundRequest;
import com.snehal.neopay.payment.entity.OrderRecord;
import com.snehal.neopay.payment.entity.Payment;
import com.snehal.neopay.payment.ledger.LedgerAccount;
import com.snehal.neopay.payment.ledger.LedgerLineRepository;
import com.snehal.neopay.payment.ledger.LedgerService;
import com.snehal.neopay.payment.repository.OrderRepository;
import com.snehal.neopay.payment.repository.PaymentRepository;
import com.snehal.neopay.payment.repository.RefundRepository;
import com.snehal.neopay.payment.service.RefundService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("integration")
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class RefundIntegrationTest {

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
    private RefundRepository refundRepository;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private LedgerLineRepository ledgerLineRepository;

    @Test
    void partialThenFullRefundBalancesLedgerAndPayment() {
        Payment payment = capturedPayment(1_000);
        ledgerService.postCapture(payment.getId(), payment.getAmount());

        refundService.create(MerchantId.of(payment.getMerchantId()),
                PaymentId.of(payment.getId()),
                new CreateRefundRequest(Money.inr(400), "first"), "refund-key-1");

        Payment afterPartial = paymentRepository.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, afterPartial.getStatus());
        assertEquals(600, ledgerLineRepository.balanceOf(LedgerAccount.MERCHANT_PAYABLE));

        refundService.create(MerchantId.of(payment.getMerchantId()),
                PaymentId.of(payment.getId()),
                new CreateRefundRequest(Money.inr(600), "second"), "refund-key-2");

        Payment afterFull = paymentRepository.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.REFUNDED, afterFull.getStatus());
        assertEquals(0, ledgerLineRepository.balanceOf(LedgerAccount.MERCHANT_PAYABLE));
        assertEquals(2, refundRepository.findByPayment_IdAndMerchantIdOrderByCreatedAtAsc(
                payment.getId(), payment.getMerchantId()).size());

        assertEquals(ledgerLineRepository.totalDebits(), ledgerLineRepository.totalCredits());
        org.junit.jupiter.api.Assertions.assertTrue(
                ledgerLineRepository.findUnbalancedTransactions().isEmpty());
    }

    @Test
    void refundAboveCapturedIsRejected() {
        Payment payment = capturedPayment(1_000);
        ledgerService.postCapture(payment.getId(), payment.getAmount());

        assertThrows(BusinessRuleViolationException.class, () -> refundService.create(
                MerchantId.of(payment.getMerchantId()), PaymentId.of(payment.getId()),
                new CreateRefundRequest(Money.inr(1_001), "too much"), "refund-key-3"));
    }

    @Test
    void replayReturnsTheSameRefund() {
        Payment payment = capturedPayment(1_000);
        ledgerService.postCapture(payment.getId(), payment.getAmount());

        var first = refundService.create(MerchantId.of(payment.getMerchantId()),
                PaymentId.of(payment.getId()),
                new CreateRefundRequest(Money.inr(100), "replay"), "refund-key-4");
        var second = refundService.create(MerchantId.of(payment.getMerchantId()),
                PaymentId.of(payment.getId()),
                new CreateRefundRequest(Money.inr(100), "replay"), "refund-key-4");

        assertEquals(first.id(), second.id());
        assertEquals(1, refundRepository.findByPayment_IdAndMerchantIdOrderByCreatedAtAsc(
                payment.getId(), payment.getMerchantId()).size());
    }

    private Payment capturedPayment(int units) {
        UUID merchantId = UUID.randomUUID();
        OrderRecord order = orderRepository.save(OrderRecord.builder()
                .merchantId(merchantId)
                .amount(Money.inr(units))
                .receipt("refund-" + UUID.randomUUID())
                .orderStatus(OrderStatus.PAID)
                .attempts(1)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build());

        return paymentRepository.save(Payment.builder()
                .order(order)
                .merchantId(merchantId)
                .amount(Money.inr(units))
                .idempotencyKey("pay-" + UUID.randomUUID())
                .status(PaymentStatus.CAPTURED)
                .method(PaymentMethod.UPI)
                .build());
    }
}
