package com.snehal.neopay.payment.domain;

import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.common.domain.PaymentId;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentAggregateTest {

    private static final PaymentId PAYMENT_ID = PaymentId.of(UUID.randomUUID());
    private static final MerchantId MERCHANT_ID = MerchantId.of(UUID.randomUUID());

    @Test
    void captureWithinAuthorizationSucceeds() {
        PaymentAggregate aggregate = aggregate(PaymentStatus.AUTHORIZED, 5_000);

        aggregate.capture(Money.inr(5_000));

        assertEquals(PaymentStatus.CAPTURED, aggregate.status());
        assertEquals(5_000, aggregate.capturedAmount().getAmountUnits());
    }

    @Test
    void captureAboveAuthorizationIsRejected() {
        PaymentAggregate aggregate = aggregate(PaymentStatus.AUTHORIZED, 5_000);

        assertThrows(IllegalArgumentException.class,
                () -> aggregate.capture(Money.inr(5_001)));
    }

    @Test
    void captureBeforeAuthorizationIsRejected() {
        PaymentAggregate aggregate = aggregate(PaymentStatus.CREATED, 5_000);

        assertThrows(IllegalStateException.class,
                () -> aggregate.capture(Money.inr(5_000)));
    }

    @Test
    void captureOfNonPositiveAmountIsRejected() {
        PaymentAggregate aggregate = aggregate(PaymentStatus.AUTHORIZED, 5_000);

        assertThrows(IllegalArgumentException.class,
                () -> aggregate.capture(Money.inr(0)));
    }

    @Test
    void refundBeforeCaptureIsRejected() {
        PaymentAggregate aggregate = aggregate(PaymentStatus.AUTHORIZED, 5_000);

        assertThrows(IllegalStateException.class,
                () -> aggregate.refund(Money.inr(100)));
    }

    @Test
    void partialRefundMovesToPartiallyRefunded() {
        PaymentAggregate aggregate = captured(5_000);

        aggregate.refund(Money.inr(2_000));

        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, aggregate.status());
        assertEquals(2_000, aggregate.refundedAmount().getAmountUnits());
    }

    @Test
    void fullRefundMovesToRefunded() {
        PaymentAggregate aggregate = captured(5_000);

        aggregate.refund(Money.inr(5_000));

        assertEquals(PaymentStatus.REFUNDED, aggregate.status());
    }

    @Test
    void cumulativeRefundAboveCaptureIsRejected() {
        PaymentAggregate aggregate = captured(5_000);
        aggregate.refund(Money.inr(4_000));

        assertThrows(IllegalStateException.class,
                () -> aggregate.refund(Money.inr(1_001)));
    }

    @Test
    void refundOfNonPositiveAmountIsRejected() {
        PaymentAggregate aggregate = captured(5_000);

        assertThrows(IllegalArgumentException.class,
                () -> aggregate.refund(Money.inr(0)));
    }

    private static PaymentAggregate aggregate(PaymentStatus status, int authorizedUnits) {
        return PaymentAggregate.rehydrate(PAYMENT_ID, MERCHANT_ID,
                Money.inr(authorizedUnits), status);
    }

    private static PaymentAggregate captured(int amountUnits) {
        PaymentAggregate aggregate = aggregate(PaymentStatus.AUTHORIZED, amountUnits);
        aggregate.capture(Money.inr(amountUnits));
        return aggregate;
    }
}
