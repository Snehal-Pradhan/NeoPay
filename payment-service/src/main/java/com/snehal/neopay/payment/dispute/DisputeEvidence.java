package com.snehal.neopay.payment.dispute;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * One evidence submission attached to a dispute.
 *
 * <p>The unique key on {@code (dispute_id, external_evidence_id)} makes the merchant-facing
 * submit operation idempotent: a retried upload cannot create a second row or a second
 * state change.
 */
@Entity
@Table(name = "dispute_evidence",
        uniqueConstraints = @UniqueConstraint(name = "uq_dispute_evidence_external",
                columnNames = {"dispute_id", "external_evidence_id"}),
        indexes = @Index(name = "idx_dispute_evidence_dispute", columnList = "dispute_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class DisputeEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "dispute_id", nullable = false)
    private UUID disputeId;

    @Column(name = "external_evidence_id", nullable = false, length = 128)
    private String externalEvidenceId;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;
}
