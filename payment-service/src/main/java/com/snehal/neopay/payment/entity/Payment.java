package com.snehal.neopay.payment.entity;

import com.snehal.neopay.common.entity.BaseEntity;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.PaymentMethod;
import com.snehal.neopay.common.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "payment", indexes = {
        @Index(name = "idx_payment_order_id", columnList = "order_id"),
        @Index(name = "idx_payment_merchant_id", columnList = "merchant_id"),
        @Index(name = "idx_payment_merchant_idempotency", columnList = "merchant_id, idempotency_key", unique = true),
        @Index(name = "idx_payment_status_created", columnList = "status, created_at")
})
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderRecord order;

    @Column(nullable = false)
    private UUID merchantId;

    @Embedded
    private Money amount;

    @Column(nullable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(nullable = false)
    private PaymentMethod method;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "method_details", columnDefinition = "jsonb")
    private Map<String, Object> methodDetails;

    @Column(length = 100)
    private String bankReference;

    @Column(length = 100)
    private String processorReference;

    @Column(length = 100)
    private String errorCode;

    @Column(length = 255)
    private String errorDescription;

    private LocalDateTime authorizedAt;

    private LocalDateTime capturedAt;

    private LocalDateTime failedAt;

    private LocalDateTime refundedAt;

    private LocalDateTime settledAt;

    public void transitionTo(PaymentStatus next) {
        this.status = next;
    }

    public void recordBankReference(String reference) {
        this.bankReference = reference;
    }

    public void recordProcessorReference(String reference) {
        this.processorReference = reference;
    }

    public void recordFailure(String code, String description) {
        this.errorCode = code;
        this.errorDescription = description;
    }

    public void markAuthorized(LocalDateTime at) {
        this.authorizedAt = at;
    }

    public void markCaptured(LocalDateTime at) {
        this.capturedAt = at;
    }

    public void markFailed(LocalDateTime at) {
        this.failedAt = at;
    }

    public void markSettled(LocalDateTime at) {
        this.settledAt = at;
    }

    public void markRefunded(LocalDateTime at) {
        this.refundedAt = at;
    }
}
