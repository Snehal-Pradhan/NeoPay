package com.snehal.neopay.payment.eventsourcing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "domain_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_domain_event_aggregate_sequence",
                columnNames = {"aggregate_id", "sequence_number"}),
        indexes = @Index(name = "idx_domain_event_aggregate", columnList = "aggregate_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DomainEventRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
