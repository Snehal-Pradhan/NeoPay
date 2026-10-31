package com.snehal.neopay.payment.webhook;

import com.snehal.neopay.common.enums.EventAggregateType;
import com.snehal.neopay.common.enums.PaymentEvent;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.payment.entity.Payment;
import com.snehal.neopay.payment.ledger.LedgerService;
import com.snehal.neopay.payment.metrics.PaymentMetrics;
import com.snehal.neopay.payment.outbox.OutboxEventPublisher;
import com.snehal.neopay.payment.repository.OrderRepository;
import com.snehal.neopay.payment.repository.PaymentRepository;
import com.snehal.neopay.payment.statemachine.PaymentTransitionService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Applies a verified Stripe event to the payment aggregate.
 *
 * <p>Only three event types change state; everything else is acknowledged and recorded
 * as {@code IGNORED}. A payment that is already terminal is left untouched, which makes
 * out-of-order delivery safe.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripePaymentEventApplier {

    static final String SUCCEEDED = "payment_intent.succeeded";
    static final String FAILED = "payment_intent.payment_failed";
    static final String CANCELED = "payment_intent.canceled";

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentTransitionService transitionService;
    private final LedgerService ledgerService;
    private final OutboxEventPublisher eventPublisher;
    private final PaymentMetrics metrics;

    /**
     * @return {@code true} when the event changed payment state, {@code false} when it
     *     was acknowledged without a transition.
     */
    @Transactional
    public boolean apply(String eventType, JsonNode root) {
        if (!isSupported(eventType)) {
            log.info("Ignoring unsupported PSP event type {}", eventType);
            return false;
        }
        JsonNode object = root.path("data").path("object");
        Payment payment = resolve(object)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", intentId(object)));

        boolean changed = dispatch(payment, eventType, object);
        if (changed) {
            persistAndPublish(payment);
        }
        return changed;
    }

    private static boolean isSupported(String eventType) {
        return SUCCEEDED.equals(eventType) || FAILED.equals(eventType) || CANCELED.equals(eventType);
    }

    private boolean dispatch(Payment payment, String eventType, JsonNode object) {
        return switch (eventType) {
            case SUCCEEDED -> onSucceeded(payment, object);
            case FAILED -> onFailed(payment, object);
            default -> onCanceled(payment);
        };
    }

    private void persistAndPublish(Payment payment) {
        paymentRepository.save(payment);
        if (payment.getOrder() != null) {
            orderRepository.save(payment.getOrder());
        }
        publish(payment);
    }

    private Optional<Payment> resolve(JsonNode object) {
        UUID paymentId = uuid(object.path("metadata").path("payment_id").asText(null));
        if (paymentId != null) {
            Optional<Payment> byId = paymentRepository.findByIdForUpdate(paymentId);
            if (byId.isPresent()) {
                return byId;
            }
        }
        return paymentRepository.findByProcessorReference(intentId(object))
                .flatMap(found -> paymentRepository.findByIdForUpdate(found.getId()));
    }

    private boolean onSucceeded(Payment payment, JsonNode object) {
        String chargeRef = object.path("latest_charge").asText(intentId(object));
        switch (payment.getStatus()) {
            case AUTHORIZING -> {
                transitionService.apply(payment, PaymentEvent.AUTHORIZE_SUCCESS);
                payment.recordBankReference(chargeRef);
                payment.markAuthorized(LocalDateTime.now());
                capture(payment);
                return true;
            }
            case AUTHORIZED -> {
                capture(payment);
                return true;
            }
            default -> {
                log.info("Ignoring succeeded intent for payment {} in status {}",
                        payment.getId(), payment.getStatus());
                return false;
            }
        }
    }

    private void capture(Payment payment) {
        transitionService.apply(payment, PaymentEvent.CAPTURE_REQUEST);
        transitionService.apply(payment, PaymentEvent.CAPTURE_SUCCESS);
        payment.markCaptured(LocalDateTime.now());
        ledgerService.postCapture(payment.getId(), payment.getAmount());
        if (payment.getOrder() != null) {
            payment.getOrder().markPaid();
        }
        metrics.paymentCaptured(payment.getMethod().name());
        log.info("Payment {} captured from a verified webhook", payment.getId());
    }

    private boolean onFailed(Payment payment, JsonNode object) {
        if (payment.getStatus() != PaymentStatus.AUTHORIZING) {
            log.info("Ignoring failed intent for payment {} in status {}",
                    payment.getId(), payment.getStatus());
            return false;
        }
        JsonNode error = object.path("last_payment_error");
        String code = textOr(error.path("decline_code"), textOr(error.path("code"), "STRIPE_PAYMENT_FAILED"));
        String message = textOr(error.path("message"), "Payment failed at the processor");
        transitionService.apply(payment, PaymentEvent.AUTHORIZE_FAIL);
        payment.recordFailure(code, message);
        metrics.paymentFailed(code);
        return true;
    }

    private boolean onCanceled(Payment payment) {
        if (payment.getStatus() != PaymentStatus.AUTHORIZING) {
            return false;
        }
        transitionService.apply(payment, PaymentEvent.CANCEL);
        return true;
    }

    private void publish(Payment payment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentId", payment.getId().toString());
        payload.put("merchantId", payment.getMerchantId().toString());
        payload.put("paymentStatus", payment.getStatus().name());
        payload.put("amountUnits", payment.getAmount().getAmountUnits());
        payload.put("amountCurrency", payment.getAmount().getCurrency());
        payload.put("paymentMethod", payment.getMethod().name());
        if (payment.getOrder() != null) {
            payload.put("orderId", payment.getOrder().getId().toString());
        }
        eventPublisher.publish(EventAggregateType.PAYMENT, payment.getId(),
                "PAYMENT_STATUS_CHANGED", payload);
    }

    private static String intentId(JsonNode object) {
        return object.path("id").asText("");
    }

    private static UUID uuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static String textOr(JsonNode node, String fallback) {
        String value = node.asText("");
        return value.isBlank() ? fallback : value;
    }
}
