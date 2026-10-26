package com.snehal.neopay.operations.repository;

import com.snehal.neopay.common.enums.OutboxStatus;
import com.snehal.neopay.operations.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
