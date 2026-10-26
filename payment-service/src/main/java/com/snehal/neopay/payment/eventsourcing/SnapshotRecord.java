package com.snehal.neopay.payment.eventsourcing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "domain_snapshot")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SnapshotRecord {

    @Id
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
