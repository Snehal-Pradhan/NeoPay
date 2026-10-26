package com.snehal.neopay.payment.domain;

import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.common.domain.PaymentId;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.PaymentStatus;

import java.util.Objects;

/**
 * Payment aggregate. All money invariants are enforced here.
 *
 * <p>Invariants:
 * <ul>
 *   <li>capture is allowed only from AUTHORIZED or CAPTURING</li>
 *   <li>captured amount never exceeds the authorized amount</li>
 *   <li>refund is allowed only after capture</li>
 *   <li>cumulative refund never exceeds the captured amount</li>
 * </ul>
 */
public final class PaymentAggregate {

    private final PaymentId id;
    private final MerchantId merchantId;
    private final Money authorizedAmount;
    private PaymentStatus status;
    private Money capturedAmount;
    private Money refundedAmount;

    private PaymentAggregate(PaymentId id, MerchantId merchantId, Money authorizedAmount,
                             PaymentStatus status, Money capturedAmount, Money refundedAmount) {
        this.id = Objects.requireNonNull(id, "id");
        this.merchantId = Objects.requireNonNull(merchantId, "merchantId");
        this.authorizedAmount = Objects.requireNonNull(authorizedAmount, "authorizedAmount");
        this.status = Objects.requireNonNull(status, "status");
        this.capturedAmount = Objects.requireNonNull(capturedAmount, "capturedAmount");
        this.refundedAmount = Objects.requireNonNull(refundedAmount, "refundedAmount");
    }

    public static PaymentAggregate rehydrate(PaymentId id, MerchantId merchantId,
                                             Money authorizedAmount, PaymentStatus status) {
        Money zero = Money.of(0, authorizedAmount.getCurrency());
        return new PaymentAggregate(id, merchantId, authorizedAmount, status, zero, zero);
    }

    public static PaymentAggregate rehydrate(PaymentId id, MerchantId merchantId,
                                             PaymentSnapshot snapshot) {
        return new PaymentAggregate(id, merchantId, snapshot.authorizedAmount(), snapshot.status(),
                snapshot.capturedAmount(), snapshot.refundedAmount());
    }

    public record PaymentSnapshot(Money authorizedAmount, PaymentStatus status,
                                  Money capturedAmount, Money refundedAmount) {
    }

    public void capture(Money amount) {
        Objects.requireNonNull(amount, "amount");
        if (status != PaymentStatus.AUTHORIZED && status != PaymentStatus.CAPTURING) {
            throw new IllegalStateException("Cannot capture payment in status " + status);
        }
        if (amount.isGreaterThan(authorizedAmount)) {
            throw new IllegalArgumentException("Capture amount exceeds authorization");
        }
        if (!amount.isGreaterThan(Money.of(0, amount.getCurrency()))) {
            throw new IllegalArgumentException("Capture amount must be positive");
        }
        this.capturedAmount = amount;
        this.status = PaymentStatus.CAPTURED;
    }

    public void refund(Money amount) {
        Objects.requireNonNull(amount, "amount");
        requireRefundable();
        if (!amount.isGreaterThan(Money.of(0, amount.getCurrency()))) {
            throw new IllegalArgumentException("Refund amount must be positive");
        }
        Money nextTotal = refundedAmount.add(amount);
        if (nextTotal.isGreaterThan(capturedAmount)) {
            throw new IllegalStateException("Refund total exceeds captured amount");
        }
        this.refundedAmount = nextTotal;
        this.status = nextTotal.equals(capturedAmount)
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED;
    }

    private void requireRefundable() {
        boolean refundable = status == PaymentStatus.CAPTURED
                || status == PaymentStatus.PARTIALLY_REFUNDED
                || status == PaymentStatus.SETTLED;
        if (!refundable) {
            throw new IllegalStateException("Cannot refund payment in status " + status);
        }
    }

    public PaymentId id() {
        return id;
    }

    public MerchantId merchantId() {
        return merchantId;
    }

    public Money authorizedAmount() {
        return authorizedAmount;
    }

    public PaymentStatus status() {
        return status;
    }

    public Money capturedAmount() {
        return capturedAmount;
    }

    public Money refundedAmount() {
        return refundedAmount;
    }
}
