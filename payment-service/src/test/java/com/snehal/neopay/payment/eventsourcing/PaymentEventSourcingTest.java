package com.snehal.neopay.payment.eventsourcing;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.OrderStatus;
import com.snehal.neopay.common.enums.PaymentEvent;
import com.snehal.neopay.common.enums.PaymentMethod;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.payment.entity.OrderRecord;
import com.snehal.neopay.payment.entity.Payment;
import com.snehal.neopay.payment.repository.OrderRepository;
import com.snehal.neopay.payment.repository.PaymentRepository;
import com.snehal.neopay.payment.statemachine.PaymentTransitionService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class PaymentEventSourcingTest {

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
    private DomainEventStore store;

    @Autowired
    private PaymentEventAppender appender;

    @Autowired
    private PaymentEventReplayer replayer;

    @Autowired
    private SnapshotRepository snapshotRepository;

    @Autowired
    private DomainEventRepository domainEventRepository;

    @Autowired
    private PaymentTransitionService transitionService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void transitionsAreAppendedAndReplayMatchesState() {
        Payment payment = persistPayment();

        transitionService.apply(payment, PaymentEvent.AUTHORIZE_ATTEMPT);
        transitionService.apply(payment, PaymentEvent.AUTHORIZE_SUCCESS);
        transitionService.apply(payment, PaymentEvent.CAPTURE_REQUEST);
        transitionService.apply(payment, PaymentEvent.CAPTURE_SUCCESS);

        assertEquals(PaymentStatus.CAPTURED, payment.getStatus());
        assertEquals(PaymentStatus.CAPTURED, replayer.replay(payment.getId()));
        assertEquals(4, replayer.eventCount(payment.getId()));
    }

    @Test
    void staleExpectedVersionIsRejected() {
        UUID aggregateId = UUID.randomUUID();
        store.append(aggregateId, "PAYMENT", 0,
                List.of(Map.of("toStatus", "AUTHORIZING")), ActorUpcaster.EVENT_TYPE);

        assertThrows(OptimisticConcurrencyException.class,
                () -> store.append(aggregateId, "PAYMENT", 5,
                        List.of(Map.of("toStatus", "AUTHORIZED")), ActorUpcaster.EVENT_TYPE));
    }

    @Test
    void snapshotIsWrittenEveryTenEventsAndReplayUsesIt() {
        UUID paymentId = UUID.randomUUID();

        for (int i = 1; i <= 10; i++) {
            PaymentStatus to = i % 2 == 0 ? PaymentStatus.AUTHORIZED : PaymentStatus.AUTHORIZING;
            appender.record(paymentId, PaymentStatus.CREATED, to,
                    PaymentEvent.AUTHORIZE_ATTEMPT, null);
        }

        assertTrue(snapshotRepository.findById(paymentId).isPresent());
        assertEquals(10, snapshotRepository.findById(paymentId).orElseThrow().getSequenceNumber());
        assertEquals(PaymentStatus.AUTHORIZED, replayer.replay(paymentId));
    }

    @Test
    void versionOnePayloadIsUpcastWithDefaultActor() {
        UUID aggregateId = UUID.randomUUID();
        domainEventRepository.save(DomainEventRecord.builder()
                .aggregateId(aggregateId)
                .aggregateType("PAYMENT")
                .sequenceNumber(1)
                .eventType(ActorUpcaster.EVENT_TYPE)
                .schemaVersion(1)
                .payload(Map.of("toStatus", "AUTHORIZING"))
                .occurredAt(LocalDateTime.now())
                .build());

        List<EventEnvelope> events = store.load(aggregateId);

        assertEquals(1, events.size());
        assertEquals(ActorUpcaster.TARGET_VERSION, events.getFirst().schemaVersion());
        assertEquals("SYSTEM", events.getFirst().payload().get("actor"));
    }

    private Payment persistPayment() {
        UUID merchantId = UUID.randomUUID();
        OrderRecord order = orderRepository.save(OrderRecord.builder()
                .merchantId(merchantId)
                .amount(Money.inr(1_000))
                .receipt("es-" + UUID.randomUUID())
                .orderStatus(OrderStatus.CREATED)
                .attempts(0)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build());

        return paymentRepository.save(Payment.builder()
                .order(order)
                .merchantId(merchantId)
                .amount(Money.inr(1_000))
                .idempotencyKey("es-" + UUID.randomUUID())
                .status(PaymentStatus.CREATED)
                .method(PaymentMethod.UPI)
                .build());
    }
}
