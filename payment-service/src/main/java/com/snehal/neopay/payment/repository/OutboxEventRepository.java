package com.snehal.neopay.payment.repository;

import com.snehal.neopay.common.enums.OutboxStatus;
import com.snehal.neopay.payment.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status);

    java.util.Optional<OutboxEvent> findFirstByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
