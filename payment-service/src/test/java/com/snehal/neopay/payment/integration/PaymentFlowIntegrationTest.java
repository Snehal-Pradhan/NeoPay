package com.snehal.neopay.payment.integration;

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
import com.snehal.neopay.payment.ledger.LedgerLineRepository;
import com.snehal.neopay.payment.repository.OutboxEventRepository;
import com.snehal.neopay.payment.repository.PaymentRepository;
import com.snehal.neopay.payment.service.PaymentService;
import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.common.domain.PaymentId;
import org.junit.jupiter.api.Tag;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@Tag("integration")
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class PaymentFlowIntegrationTest {

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

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private LedgerLineRepository ledgerLineRepository;

    @Test
    void initiateAuthorizeCaptureUpdatesOrderAndWritesOutbox() {
        UUID merchantId = UUID.randomUUID();
        OrderRecord order = orderRepository.save(OrderRecord.builder()
                .merchantId(merchantId)
                .amount(Money.inr(5_000))
                .receipt("flow-" + UUID.randomUUID())
                .orderStatus(OrderStatus.CREATED)
                .attempts(0)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build());

        when(gatewayRouter.initiate(any()))
                .thenReturn(new PaymentResult.Pending("UPI_PROCESSOR_test"));
        when(gatewayRouter.capture(eq(PaymentMethod.UPI), any()))
                .thenReturn(new PaymentResult.Success("SIM_BANK_REF_test"));

        PaymentInitRequest request = new PaymentInitRequest(order.getId(),
                PaymentMethod.UPI, Map.of("vpa", "success@okaxis"));

        PaymentResponse initiated = paymentService.initiate(MerchantId.of(merchantId), request, "flow-key-1");
        assertEquals(PaymentStatus.AUTHORIZING, initiated.status());
        assertNotNull(initiated.id());

        paymentService.resolveAuthorization(PaymentId.of(initiated.id()), true, "SIM_BANK_REF_test", null, null);

        var finalState = paymentRepository.findById(initiated.id()).orElseThrow();
        assertEquals(PaymentStatus.CAPTURED, finalState.getStatus());
        assertNotNull(finalState.getCapturedAt());

        OrderRecord reloadedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.PAID, reloadedOrder.getOrderStatus());

        assertTrue(outboxEventRepository.count() > 0, "outbox rows must be written");
        assertEquals(2, ledgerLineRepository
                .findByReferenceTypeAndReferenceId("PAYMENT", initiated.id()).size(),
                "capture must post a balanced ledger pair");
        assertEquals(1, paymentRepository.count());
    }
}
