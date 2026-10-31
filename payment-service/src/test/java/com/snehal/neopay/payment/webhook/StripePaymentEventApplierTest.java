package com.snehal.neopay.payment.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.EventAggregateType;
import com.snehal.neopay.common.enums.OrderStatus;
import com.snehal.neopay.common.enums.PaymentEvent;
import com.snehal.neopay.common.enums.PaymentMethod;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.payment.entity.OrderRecord;
import com.snehal.neopay.payment.entity.Payment;
import com.snehal.neopay.payment.ledger.LedgerService;
import com.snehal.neopay.payment.metrics.PaymentMetrics;
import com.snehal.neopay.payment.outbox.OutboxEventPublisher;
import com.snehal.neopay.payment.repository.OrderRepository;
import com.snehal.neopay.payment.repository.PaymentRepository;
import com.snehal.neopay.payment.statemachine.PaymentTransitionService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StripePaymentEventApplierTest {

    private static final UUID MERCHANT = UUID.randomUUID();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final PaymentTransitionService transitionService = mock(PaymentTransitionService.class);
    private final LedgerService ledgerService = mock(LedgerService.class);
    private final OutboxEventPublisher eventPublisher = mock(OutboxEventPublisher.class);
    private final PaymentMetrics metrics = mock(PaymentMetrics.class);

    private final StripePaymentEventApplier applier = new StripePaymentEventApplier(
            paymentRepository, orderRepository, transitionService, ledgerService,
            eventPublisher, metrics);

    @Test
    void succeededIntentAuthorizesCapturesAndPostsLedger() {
        Payment payment = payment(PaymentStatus.AUTHORIZING);
        when(paymentRepository.findByIdForUpdate(payment.getId()))
                .thenReturn(Optional.of(payment));

        boolean changed = applier.apply(StripePaymentEventApplier.SUCCEEDED,
                event(payment.getId(), "pi_1", "ch_1"));

        assertTrue(changed);
        InOrder order = inOrder(transitionService);
        order.verify(transitionService).apply(payment, PaymentEvent.AUTHORIZE_SUCCESS);
        order.verify(transitionService).apply(payment, PaymentEvent.CAPTURE_REQUEST);
        order.verify(transitionService).apply(payment, PaymentEvent.CAPTURE_SUCCESS);
        verify(ledgerService).postCapture(payment.getId(), payment.getAmount());
        verify(metrics).paymentCaptured("UPI");
        verify(paymentRepository).save(payment);
        verify(orderRepository).save(payment.getOrder());
        verify(eventPublisher).publish(eq(EventAggregateType.PAYMENT), eq(payment.getId()),
                eq("PAYMENT_STATUS_CHANGED"), any());
        assertEquals("ch_1", payment.getBankReference());
    }

    @Test
    void succeededIntentOnAuthorizedPaymentOnlyCaptures() {
        Payment payment = payment(PaymentStatus.AUTHORIZED);
        when(paymentRepository.findByIdForUpdate(payment.getId()))
                .thenReturn(Optional.of(payment));

        assertTrue(applier.apply(StripePaymentEventApplier.SUCCEEDED,
                event(payment.getId(), "pi_2", "ch_2")));

        verify(transitionService, never()).apply(payment, PaymentEvent.AUTHORIZE_SUCCESS);
        verify(transitionService).apply(payment, PaymentEvent.CAPTURE_REQUEST);
    }

    @Test
    void succeededIntentOnCapturedPaymentIsANoOp() {
        Payment payment = payment(PaymentStatus.CAPTURED);
        when(paymentRepository.findByIdForUpdate(payment.getId()))
                .thenReturn(Optional.of(payment));

        assertFalse(applier.apply(StripePaymentEventApplier.SUCCEEDED,
                event(payment.getId(), "pi_3", "ch_3")));

        verifyNoInteractions(transitionService, ledgerService, eventPublisher);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void failedIntentRecordsDeclineCodeAndMessages() {
        Payment payment = payment(PaymentStatus.AUTHORIZING);
        when(paymentRepository.findByIdForUpdate(payment.getId()))
                .thenReturn(Optional.of(payment));

        assertTrue(applier.apply(StripePaymentEventApplier.FAILED,
                failureEvent(payment.getId(), "pi_4", "insufficient_funds",
                        "Your card has insufficient funds.")));

        assertEquals("insufficient_funds", payment.getErrorCode());
        assertEquals("Your card has insufficient funds.", payment.getErrorDescription());
        verify(transitionService).apply(payment, PaymentEvent.AUTHORIZE_FAIL);
        verify(metrics).paymentFailed("insufficient_funds");
    }

    @Test
    void failedIntentOnCapturedPaymentIsIgnored() {
        Payment payment = payment(PaymentStatus.CAPTURED);
        when(paymentRepository.findByIdForUpdate(payment.getId()))
                .thenReturn(Optional.of(payment));

        assertFalse(applier.apply(StripePaymentEventApplier.FAILED,
                failureEvent(payment.getId(), "pi_5", "card_declined", "declined")));

        verifyNoInteractions(transitionService, metrics);
    }

    @Test
    void canceledIntentCancelsOnlyAuthorizingPayment() {
        Payment authorizing = payment(PaymentStatus.AUTHORIZING);
        when(paymentRepository.findByIdForUpdate(authorizing.getId()))
                .thenReturn(Optional.of(authorizing));

        assertTrue(applier.apply(StripePaymentEventApplier.CANCELED,
                event(authorizing.getId(), "pi_6", null)));
        verify(transitionService).apply(authorizing, PaymentEvent.CANCEL);

        Payment captured = payment(PaymentStatus.CAPTURED);
        when(paymentRepository.findByIdForUpdate(captured.getId()))
                .thenReturn(Optional.of(captured));
        assertFalse(applier.apply(StripePaymentEventApplier.CANCELED,
                event(captured.getId(), "pi_7", null)));
    }

    @Test
    void unrelatedEventTypeIsNotApplied() {
        assertFalse(applier.apply("charge.refunded", event(UUID.randomUUID(), "pi_8", null)));
        verifyNoInteractions(paymentRepository, transitionService);
    }

    @Test
    void paymentIsResolvedByProcessorReferenceWhenMetadataIsAbsent() {
        Payment payment = payment(PaymentStatus.AUTHORIZING);
        when(paymentRepository.findByProcessorReference("pi_ref"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.findByIdForUpdate(payment.getId()))
                .thenReturn(Optional.of(payment));

        JsonNode withoutMetadata = MAPPER.createObjectNode()
                .set("data", MAPPER.createObjectNode().set("object",
                        MAPPER.createObjectNode().put("id", "pi_ref")));

        assertTrue(applier.apply(StripePaymentEventApplier.CANCELED, withoutMetadata));
        verify(paymentRepository).findByProcessorReference("pi_ref");
    }

    @Test
    void unknownPaymentIsReported() {
        JsonNode unknown = event(UUID.randomUUID(), "pi_unknown", null);
        when(paymentRepository.findByIdForUpdate(any())).thenReturn(Optional.empty());
        when(paymentRepository.findByProcessorReference("pi_unknown")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> applier.apply(StripePaymentEventApplier.SUCCEEDED, unknown));
    }

    @Test
    void invalidMetadataFallsBackToReferenceLookup() {
        JsonNode root = MAPPER.createObjectNode()
                .set("data", MAPPER.createObjectNode().set("object",
                        MAPPER.createObjectNode()
                                .put("id", "pi_bad_meta")
                                .set("metadata", MAPPER.createObjectNode()
                                        .put("payment_id", "not-a-uuid"))));
        when(paymentRepository.findByProcessorReference("pi_bad_meta")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> applier.apply(StripePaymentEventApplier.SUCCEEDED, root));
    }

    private static JsonNode event(UUID paymentId, String intentId, String chargeId) {
        com.fasterxml.jackson.databind.node.ObjectNode object = MAPPER.createObjectNode();
        object.put("id", intentId);
        object.set("metadata", MAPPER.createObjectNode().put("payment_id", paymentId.toString()));
        if (chargeId != null) {
            object.put("latest_charge", chargeId);
        }
        return MAPPER.createObjectNode()
                .set("data", MAPPER.createObjectNode().set("object", object));
    }

    private static JsonNode failureEvent(UUID paymentId, String intentId, String code,
                                         String message) {
        com.fasterxml.jackson.databind.node.ObjectNode object = MAPPER.createObjectNode();
        object.put("id", intentId);
        object.set("metadata", MAPPER.createObjectNode().put("payment_id", paymentId.toString()));
        object.set("last_payment_error", MAPPER.createObjectNode()
                .put("decline_code", code)
                .put("message", message));
        return MAPPER.createObjectNode()
                .set("data", MAPPER.createObjectNode().set("object", object));
    }

    private static Payment payment(PaymentStatus status) {
        OrderRecord order = OrderRecord.builder()
                .id(UUID.randomUUID())
                .merchantId(MERCHANT)
                .amount(Money.inr(1_000))
                .receipt("r")
                .orderStatus(OrderStatus.CREATED)
                .attempts(1)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
        return Payment.builder()
                .id(UUID.randomUUID())
                .order(order)
                .merchantId(MERCHANT)
                .amount(Money.inr(1_000))
                .idempotencyKey("key")
                .status(status)
                .method(PaymentMethod.UPI)
                .build();
    }
}
