package com.snehal.neopay.payment.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Durable record of every inbound PSP event.
 *
 * <p>The unique constraint on {@code (provider, external_event_id)} is the replay guard:
 * a redelivered event cannot produce a second state transition or a second ledger post.
 */
@Entity
@Table(name = "webhook_event",
        uniqueConstraints = @UniqueConstraint(name = "uq_webhook_provider_event",
                columnNames = {"provider", "external_event_id"}),
        indexes = @Index(name = "idx_webhook_status_received", columnList = "status, received_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class StripeWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "external_event_id", nullable = false, length = 128)
    private String externalEventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WebhookStatus status;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(length = 255)
    private String error;

    public void markProcessed(LocalDateTime at) {
        this.status = WebhookStatus.PROCESSED;
        this.processedAt = at;
        this.error = null;
    }

    public void markIgnored(LocalDateTime at) {
        this.status = WebhookStatus.IGNORED;
        this.processedAt = at;
    }

    public void markFailed(String reason, LocalDateTime at) {
        this.status = WebhookStatus.FAILED;
        this.processedAt = at;
        this.error = reason == null ? null : reason.substring(0, Math.min(reason.length(), 255));
    }
}
