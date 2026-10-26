package com.snehal.neopay.payment.eventsourcing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DomainEventRepository extends JpaRepository<DomainEventRecord, UUID> {

    List<DomainEventRecord> findByAggregateIdOrderBySequenceNumberAsc(UUID aggregateId);

    Optional<DomainEventRecord> findTopByAggregateIdOrderBySequenceNumberDesc(UUID aggregateId);
}
