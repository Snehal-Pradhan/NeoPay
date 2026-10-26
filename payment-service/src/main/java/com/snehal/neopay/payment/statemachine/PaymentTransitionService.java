package com.snehal.neopay.payment.statemachine;

import com.snehal.neopay.common.context.MerchantContext;
import com.snehal.neopay.common.enums.PaymentActor;
import com.snehal.neopay.common.enums.PaymentEvent;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.payment.entity.Payment;
import com.snehal.neopay.payment.eventsourcing.PaymentEventAppender;
import com.snehal.neopay.payment.entity.PaymentTransitionLog;
import com.snehal.neopay.payment.repository.PaymentTransitionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentTransitionService {

    private final PaymentTransitionLogRepository paymentTransitionLogRepository;
    private final PaymentStateMachine paymentStateMachine;
    private final MerchantContext merchantContext;
    private final PaymentEventAppender eventAppender;

    public PaymentStatus apply(Payment payment, PaymentEvent event) {
        PaymentStatus from = payment.getStatus();
        PaymentStatus next = paymentStateMachine.transition(from, event);
        PaymentActor actor = getPaymentActor();
        PaymentTransitionLog log = PaymentTransitionLog.builder()
                .payment(payment)
                .fromStatus(payment.getStatus())
                .event(event)
                .toStatus(next)
                .actor(actor)
                .occurredAt(LocalDateTime.now())
                .build();
        payment.transitionTo(next);
        paymentTransitionLogRepository.save(log);
        if (payment.getId() != null) {
            eventAppender.record(payment.getId(), from, next, event, actor);
        }
        return next;
    }

    private PaymentActor getPaymentActor() {
        try {
            String keyId = merchantContext.getKeyId();
            UUID merchantId = merchantContext.getMerchantId();

            if (keyId != null && !keyId.isBlank()) {
                return PaymentActor.CUSTOMER;
            } else if (merchantId != null) {
                return PaymentActor.MERCHANT;
            }
        } catch (Exception ignored) {
        }
        return PaymentActor.SYSTEM;
    }
}
