package com.snehal.neopay.payment.dispute;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.payment.ledger.LedgerAccount;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "dispute", indexes = {
        @Index(name = "idx_dispute_payment", columnList = "payment_id"),
        @Index(name = "idx_dispute_merchant", columnList = "merchant_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amountUnits", column = @Column(name = "amount_units", nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = "amount_currency", nullable = false))
    })
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DisputeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_account", nullable = false, length = 30)
    private LedgerAccount sourceAccount;

    @Column(length = 255)
    private String reason;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public void markUnderReview() {
        this.status = DisputeStatus.UNDER_REVIEW;
    }

    public void resolve(DisputeStatus outcome, LocalDateTime at) {
        if (outcome != DisputeStatus.WON && outcome != DisputeStatus.LOST) {
            throw new IllegalArgumentException("Outcome must be WON or LOST");
        }
        this.status = outcome;
        this.resolvedAt = at;
    }
}
