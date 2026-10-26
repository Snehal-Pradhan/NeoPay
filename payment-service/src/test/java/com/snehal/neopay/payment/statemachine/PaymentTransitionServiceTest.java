package com.snehal.neopay.payment.statemachine;

import com.snehal.neopay.common.context.MerchantContext;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.PaymentActor;
import com.snehal.neopay.common.enums.PaymentEvent;
import com.snehal.neopay.common.enums.PaymentMethod;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.payment.entity.Payment;
import com.snehal.neopay.payment.entity.PaymentTransitionLog;
import com.snehal.neopay.payment.eventsourcing.PaymentEventAppender;
import com.snehal.neopay.payment.repository.PaymentTransitionLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentTransitionServiceTest {

    private final PaymentTransitionLogRepository logRepository =
            mock(PaymentTransitionLogRepository.class);
    private final PaymentStateMachine stateMachine = mock(PaymentStateMachine.class);
    private final MerchantContext merchantContext = new MerchantContext();
    private final PaymentEventAppender eventAppender = mock(PaymentEventAppender.class);

    private final PaymentTransitionService service = new PaymentTransitionService(
            logRepository, stateMachine, merchantContext, eventAppender);

    @Test
    void transitionPersistsLogAndEvent() {
        Payment payment = payment(UUID.randomUUID());
        when(stateMachine.transition(PaymentStatus.CREATED, PaymentEvent.AUTHORIZE_ATTEMPT))
                .thenReturn(PaymentStatus.AUTHORIZING);
        merchantContext.setKeyId("np_test_1");

        PaymentStatus next = service.apply(payment, PaymentEvent.AUTHORIZE_ATTEMPT);

        assertEquals(PaymentStatus.AUTHORIZING, next);
        assertEquals(PaymentStatus.AUTHORIZING, payment.getStatus());

        ArgumentCaptor<PaymentTransitionLog> captor =
                ArgumentCaptor.forClass(PaymentTransitionLog.class);
        verify(logRepository).save(captor.capture());
        assertEquals(PaymentStatus.CREATED, captor.getValue().getFromStatus());
        assertEquals(PaymentActor.CUSTOMER, captor.getValue().getActor());
        verify(eventAppender).record(payment.getId(), PaymentStatus.CREATED,
                PaymentStatus.AUTHORIZING, PaymentEvent.AUTHORIZE_ATTEMPT, PaymentActor.CUSTOMER);
    }

    @Test
    void merchantCallerIsRecordedWhenNoApiKeyIsPresent() {
        Payment payment = payment(UUID.randomUUID());
        when(stateMachine.transition(any(), any())).thenReturn(PaymentStatus.AUTHORIZING);
        merchantContext.setMerchantId(UUID.randomUUID());

        service.apply(payment, PaymentEvent.AUTHORIZE_ATTEMPT);

        assertEquals(PaymentActor.MERCHANT, savedActor());
    }

    @Test
    void missingContextFallsBackToSystemActor() {
        Payment payment = payment(UUID.randomUUID());
        when(stateMachine.transition(any(), any())).thenReturn(PaymentStatus.AUTHORIZING);

        service.apply(payment, PaymentEvent.AUTHORIZE_ATTEMPT);

        assertEquals(PaymentActor.SYSTEM, savedActor());
    }

    @Test
    void contextFailureFallsBackToSystemActor() {
        Payment payment = payment(UUID.randomUUID());
        when(stateMachine.transition(any(), any())).thenReturn(PaymentStatus.AUTHORIZING);
        MerchantContext failing = mock(MerchantContext.class);
        when(failing.getKeyId()).thenThrow(new IllegalStateException("no request scope"));
        PaymentTransitionService failingService = new PaymentTransitionService(
                logRepository, stateMachine, failing, eventAppender);

        failingService.apply(payment, PaymentEvent.AUTHORIZE_ATTEMPT);

        assertEquals(PaymentActor.SYSTEM, savedActor());
    }

    @Test
    void unsavedPaymentSkipsEventAppend() {
        Payment payment = payment(null);
        when(stateMachine.transition(any(), any())).thenReturn(PaymentStatus.AUTHORIZING);

        service.apply(payment, PaymentEvent.AUTHORIZE_ATTEMPT);

        verify(eventAppender, never()).record(any(), any(), any(), any(), any());
        verify(logRepository).save(any());
    }

    @Test
    void whitespaceKeyIdIsNotTreatedAsCustomer() {
        Payment payment = payment(UUID.randomUUID());
        when(stateMachine.transition(any(), any())).thenReturn(PaymentStatus.AUTHORIZING);
        merchantContext.setKeyId("   ");
        merchantContext.setMerchantId(UUID.randomUUID());

        service.apply(payment, PaymentEvent.AUTHORIZE_ATTEMPT);

        assertEquals(PaymentActor.MERCHANT, savedActor());
    }

    private PaymentActor savedActor() {
        ArgumentCaptor<PaymentTransitionLog> captor =
                ArgumentCaptor.forClass(PaymentTransitionLog.class);
        verify(logRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue().getActor();
    }

    private static Payment payment(UUID id) {
        return Payment.builder()
                .id(id)
                .merchantId(UUID.randomUUID())
                .amount(Money.inr(100))
                .idempotencyKey("k")
                .status(PaymentStatus.CREATED)
                .method(PaymentMethod.UPI)
                .build();
    }
}
