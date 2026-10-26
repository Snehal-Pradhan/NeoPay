package com.snehal.neopay.payment.entity;

import com.snehal.neopay.common.entity.BaseEntity;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.RefundStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "refund",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_refund_payment_idempotency",
                columnNames = {"payment_id", "idempotency_key"}),
        indexes = @Index(name = "idx_refund_payment", columnList = "payment_id"))
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Refund extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Embedded
    private Money amount;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RefundStatus status = RefundStatus.PENDING;

    @Column(length = 255)
    private String reason;

    @Column(length = 100)
    private String bankReference;

    @Column(length = 100)
    private String errorCode;

    @Column(length = 500)
    private String errorDescription;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> notes;

    private LocalDateTime processedAt;

    public void markProcessed(String reference, LocalDateTime at) {
        this.status = RefundStatus.PROCESSED;
        this.bankReference = reference;
        this.processedAt = at;
    }

    public void markFailed(String code, String description) {
        this.status = RefundStatus.FAILED;
        this.errorCode = code;
        this.errorDescription = description;
    }
}
